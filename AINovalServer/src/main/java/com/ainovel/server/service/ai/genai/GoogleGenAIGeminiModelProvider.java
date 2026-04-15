package com.ainovel.server.service.ai.genai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.ainovel.server.domain.model.AIRequest;
import com.ainovel.server.domain.model.AIResponse;
import com.ainovel.server.service.ai.AbstractAIModelProvider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;
import reactor.util.retry.Retry;

/**
 * 使用官方 Gemini REST 接口（与 Google GenAI SDK 对齐的数据结构）
 * 高可扩展：
 * - 支持函数调用（tools + tool_config.function_calling）
 * - 预留参数映射入口，后续可切换至 google-genai SDK 而不改业务层
 */
@Slf4j
public class GoogleGenAIGeminiModelProvider extends AbstractAIModelProvider {

    private static final String DEFAULT_API_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private WebClient webClient;
    private final String baseUrl;
    // 简易上下文缓存（节点内存级），可通过 parameters.context.cacheId 使用
    private static final ConcurrentHashMap<String, List<Map<String, Object>>> CONTEXT_CACHE = new ConcurrentHashMap<>();

    // 可插拔请求体扩展点
    @FunctionalInterface
    public interface RequestBodyMutator {
        void mutate(Map<String, Object> requestBody, AIRequest request);
    }
    private final List<RequestBodyMutator> requestMutators = new ArrayList<>();

    public GoogleGenAIGeminiModelProvider(String modelName, String apiKey, String apiEndpoint) {
        super("gemini", modelName, apiKey, apiEndpoint);
        this.baseUrl = getApiEndpoint(DEFAULT_API_ENDPOINT);
        initWebClient();
        // 注册默认扩展：函数调用配置覆盖、上下文缓存、MCP工具桥接（通过参数注入）
        registerDefaultMutators();
    }

    private void initWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(120))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);

        if (proxyEnabled && proxyHost != null && proxyPort > 0) {
            try {
                httpClient = httpClient
                        .proxy(spec -> spec
                                .type(ProxyProvider.Proxy.HTTP)
                                .host(proxyHost)
                                .port(proxyPort));

                if (trustAllCerts) {
                    SslContext sslContext = SslContextBuilder
                            .forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .build();
                    httpClient = httpClient.secure(t -> t.sslContext(sslContext));
                    log.warn("GoogleGenAIGemini: 已启用 trustAllCerts（仅建议用于排障），生产请关闭！");
                }

                log.info("GoogleGenAIGemini: 已启用代理: {}:{}", proxyHost, proxyPort);
            } catch (Exception e) {
                log.error("GoogleGenAIGemini: 配置代理时出错: {}", e.getMessage(), e);
            }
        }

        this.webClient = WebClient.builder()
                .baseUrl(this.baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        log.info("GoogleGenAIGemini: WebClient已初始化，基础URL: {}", this.baseUrl);
    }

    @Override
    public void setProxy(String host, int port) {
        super.setProxy(host, port);
        initWebClient();
    }

    @Override
    public void disableProxy() {
        super.disableProxy();
        initWebClient();
    }

    @Override
    public Mono<AIResponse> generateContent(AIRequest request) {
        if (isApiKeyEmpty()) {
            AIResponse errorResponse = createBaseResponse("API密钥未配置", request);
            errorResponse.setFinishReason("error");
            return Mono.just(errorResponse);
        }

        try {
            Map<String, Object> requestBody = buildGenAiRequestBody(request, false);
            log.info("开始Gemini非流式请求, 模型: {}, 请求体keys: {}", modelName, requestBody.keySet());

            return webClient.post()
                    .uri("/{model}:generateContent?key={apiKey}", modelName, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(responseJson -> {
                        try {
                            GenAiResponse resp = objectMapper.readValue(responseJson, GenAiResponse.class);
                            return convertToAIResponse(resp, request);
                        } catch (Exception e) {
                            log.error("解析Gemini响应失败: {}", e.getMessage(), e);
                            AIResponse errorResponse = createBaseResponse("解析响应失败: " + e.getMessage(), request);
                            errorResponse.setFinishReason("error");
                            return errorResponse;
                        }
                    })
                    .retryWhen(Retry.backoff(1, Duration.ofSeconds(2)))
                    .onErrorResume(e -> {
                        log.error("Gemini API调用失败: {}", e.getMessage(), e);
                        return handleApiException(e, request);
                    });
        } catch (Exception e) {
            log.error("Gemini API调用失败(构建请求体阶段): {}", e.getMessage(), e);
            return handleApiException(e, request);
        }
    }

    @Override
    public Flux<String> generateContentStream(AIRequest request) {
        if (isApiKeyEmpty()) {
            return Flux.just("错误：API密钥未配置");
        }

        final long requestStartTime = System.currentTimeMillis();
        final AtomicLong firstChunkTime = new AtomicLong(0);
        final AtomicBoolean hasReceivedContent = new AtomicBoolean(false);

        try {
            Map<String, Object> requestBody = buildGenAiRequestBody(request, true);
            log.info("开始Gemini流式请求, 模型: {}, 请求体keys: {}", modelName, requestBody.keySet());

            Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
            final StringBuilder sseBuffer = new StringBuilder();

            webClient.post()
                    .uri("/{model}:streamGenerateContent?key={apiKey}", modelName, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .subscribe(
                        chunk -> {
                            try {
                                if (chunk == null || chunk.isEmpty()) return;
                                // 归一化换行并追加到缓冲
                                sseBuffer.append(chunk.replace("\r\n", "\n"));
                                // 以空行作为一个event的边界（SSE规范）
                                int idx;
                                boolean processedAny = false;
                                while ((idx = sseBuffer.indexOf("\n\n")) >= 0) {
                                    String eventBlock = sseBuffer.substring(0, idx);
                                    sseBuffer.delete(0, idx + 2);

                                    // 收集该event中的所有data行，按顺序拼接
                                    String[] lines = eventBlock.split("\n");
                                    StringBuilder dataPayload = new StringBuilder();
                                    for (String line : lines) {
                                        if (line == null) continue;
                                        String s = line.trim();
                                        if (s.isEmpty() || s.startsWith("event:") || s.startsWith(":") || s.startsWith("id:")) continue;
                                        if (s.startsWith("data:")) {
                                            s = s.substring(5).trim();
                                            if (!s.isEmpty()) {
                                                if (dataPayload.length() > 0) dataPayload.append('\n');
                                                dataPayload.append(s);
                                            }
                                        }
                                    }
                                    String json = dataPayload.toString().trim();
                                    if (json.isEmpty() || "[DONE]".equals(json)) continue;

                                    boolean emitted = false;
                                    try {
                                        GenAiResponse resp = objectMapper.readValue(json, GenAiResponse.class);
                                        String partial = extractFirstText(resp);
                                        if (partial != null && !partial.isEmpty()) {
                                            if (firstChunkTime.get() == 0) {
                                                firstChunkTime.set(System.currentTimeMillis());
                                                hasReceivedContent.set(true);
                                                log.info("Gemini: 收到首个响应, 耗时: {}ms", firstChunkTime.get() - requestStartTime);
                                            }
                                            sink.tryEmitNext(partial);
                                            emitted = true;
                                            processedAny = true;
                                        }
                                    } catch (Exception ignore) {}
                                    if (!emitted && json.startsWith("[")) {
                                        try {
                                            List<GenAiResponse> list = objectMapper.readValue(json, new TypeReference<List<GenAiResponse>>(){});
                                            for (GenAiResponse r : list) {
                                                String partial = extractFirstText(r);
                                                if (partial != null && !partial.isEmpty()) {
                                                    if (firstChunkTime.get() == 0) {
                                                        firstChunkTime.set(System.currentTimeMillis());
                                                        hasReceivedContent.set(true);
                                                        log.info("Gemini: 收到首个响应, 耗时: {}ms", firstChunkTime.get() - requestStartTime);
                                                    }
                                                    sink.tryEmitNext(partial);
                                                }
                                            }
                                            emitted = true;
                                            processedAny = true;
                                        } catch (Exception ignore) {}
                                    }
                                    if (!emitted) {
                                        log.error("解析Gemini流式响应失败: 非法JSON片段前缀={}, 长度={}", json.length() > 10 ? json.substring(0,10) : json, json.length());
                                        sink.tryEmitNext("错误：流响应解析失败");
                                    }
                                }
                                // 若没有空行分隔，尝试直接从缓冲提取 data:
                                if (!processedAny) {
                                    String[] lines = sseBuffer.toString().split("\n");
                                    StringBuilder dataPayload = new StringBuilder();
                                    for (String line : lines) {
                                        if (line == null) continue;
                                        String s = line.trim();
                                        if (s.startsWith("data:")) {
                                            s = s.substring(5).trim();
                                            if (!s.isEmpty()) {
                                                if (dataPayload.length() > 0) dataPayload.append('\n');
                                                dataPayload.append(s);
                                            }
                                        }
                                    }
                                    String json = dataPayload.toString().trim();
                                    if (!json.isEmpty() && !"[DONE]".equals(json)) {
                                        boolean emitted = false;
                                        try {
                                            GenAiResponse resp = objectMapper.readValue(json, GenAiResponse.class);
                                            String partial = extractFirstText(resp);
                                            if (partial != null && !partial.isEmpty()) {
                                                if (firstChunkTime.get() == 0) {
                                                    firstChunkTime.set(System.currentTimeMillis());
                                                    hasReceivedContent.set(true);
                                                    log.info("Gemini: 收到首个响应(无空行边界), 耗时: {}ms", firstChunkTime.get() - requestStartTime);
                                                }
                                                sink.tryEmitNext(partial);
                                                emitted = true;
                                            }
                                        } catch (Exception ignore) {}
                                        if (!emitted && json.startsWith("[")) {
                                            try {
                                                List<GenAiResponse> list = objectMapper.readValue(json, new TypeReference<List<GenAiResponse>>(){});
                                                for (GenAiResponse r : list) {
                                                    String partial = extractFirstText(r);
                                                    if (partial != null && !partial.isEmpty()) {
                                                        if (firstChunkTime.get() == 0) {
                                                            firstChunkTime.set(System.currentTimeMillis());
                                                            hasReceivedContent.set(true);
                                                            log.info("Gemini: 收到首个响应(无空行边界), 耗时: {}ms", firstChunkTime.get() - requestStartTime);
                                                        }
                                                        sink.tryEmitNext(partial);
                                                    }
                                                }
                                                emitted = true;
                                            } catch (Exception ignore) {}
                                        }
                                        if (emitted) {
                                            sseBuffer.setLength(0);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.error("解析Gemini流式响应失败: {}", e.getMessage(), e);
                                sink.tryEmitNext("错误：" + e.getMessage());
                            }
                        },
                        error -> {
                            log.error("Gemini流式API调用失败: {}", error.getMessage(), error);
                            sink.tryEmitNext("错误：" + error.getMessage());
                            sink.tryEmitComplete();
                        },
                        () -> {
                            log.info("Gemini流式生成完成，总耗时: {}ms", System.currentTimeMillis() - requestStartTime);
                            try {
                                String[] lines = sseBuffer.toString().split("\n");
                                StringBuilder dataPayload = new StringBuilder();
                                for (String line : lines) {
                                    if (line == null) continue;
                                    String s = line.trim();
                                    if (s.startsWith("data:")) {
                                        s = s.substring(5).trim();
                                        if (!s.isEmpty()) {
                                            if (dataPayload.length() > 0) dataPayload.append('\n');
                                            dataPayload.append(s);
                                        }
                                    }
                                }
                                String json = dataPayload.toString().trim();
                                if (!json.isEmpty() && !"[DONE]".equals(json)) {
                                    try {
                                        GenAiResponse resp = objectMapper.readValue(json, GenAiResponse.class);
                                        String partial = extractFirstText(resp);
                                        if (partial != null && !partial.isEmpty()) {
                                            sink.tryEmitNext(partial);
                                        }
                                    } catch (Exception ignore) {
                                        try {
                                            List<GenAiResponse> list = objectMapper.readValue(json, new TypeReference<List<GenAiResponse>>(){});
                                            for (GenAiResponse r : list) {
                                                String partial = extractFirstText(r);
                                                if (partial != null && !partial.isEmpty()) {
                                                    sink.tryEmitNext(partial);
                                                }
                                            }
                                        } catch (Exception ignore2) {}
                                    }
                                }
                            } catch (Exception ignore) {}
                            sink.tryEmitComplete();
                        }
                    );

            return sink.asFlux()
                    .timeout(Duration.ofSeconds(300))
                    .retryWhen(Retry.backoff(1, Duration.ofSeconds(2)))
                    .onErrorResume(e -> {
                        log.error("流式生成内容时出错: {}", e.getMessage(), e);
                        return Flux.just("错误：" + e.getMessage());
                    });
        } catch (Exception e) {
            log.error("Gemini流式API调用失败(构建请求体阶段): {}", e.getMessage(), e);
            return Flux.just("错误：" + e.getMessage());
        }
    }

    @Override
    public Mono<Boolean> validateApiKey() {
        if (isApiKeyEmpty()) {
            return Mono.just(false);
        }

        try {
            return webClient.get()
                    .uri("?key={apiKey}", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(resp -> true)
                    .onErrorReturn(false);
        } catch (Exception e) {
            log.error("验证Gemini API密钥失败: {}", e.getMessage(), e);
            return Mono.just(false);
        }
    }

    @Override
    public Mono<Double> estimateCost(AIRequest request) {
        // 参考 https://ai.google.dev/pricing
        double inputPer1k = 0.000125; // USD / 1K tokens（示例值）
        double outputPer1k = 0.000375; // USD / 1K tokens（示例值）

        int inputTokens = estimateInputTokens(request);
        int outputTokens = request.getMaxTokens() != null ? request.getMaxTokens() : 1000;

        double costUsd = (inputTokens / 1000.0) * inputPer1k + (outputTokens / 1000.0) * outputPer1k;
        double costCny = costUsd * 7.2;
        return Mono.just(costCny);
    }

    /**
     * 简单估算输入tokens。
     * 为保证独立性，不依赖上游分词器：
     * - 系统prompt按空白分词 * 1.3
     * - 历史消息累加同策略
     */
    private int estimateInputTokens(AIRequest request) {
        int tokenCount = 0;
        if (request.getPrompt() != null && !request.getPrompt().isEmpty()) {
            tokenCount += estimateTokenCount(request.getPrompt());
        }
        if (request.getMessages() != null) {
            for (AIRequest.Message m : request.getMessages()) {
                if (m.getContent() != null) {
                    tokenCount += estimateTokenCount(m.getContent());
                }
            }
        }
        return tokenCount;
    }

    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) (text.split("\\s+").length * 1.3);
    }

    private Map<String, Object> buildGenAiRequestBody(AIRequest request, boolean isStream) {
        Map<String, Object> body = new HashMap<>();

        // contents
        body.put("contents", convertMessages(request));

        // generationConfig
        Map<String, Object> generationConfig = new HashMap<>();
        if (request.getTemperature() != null) {
            generationConfig.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            generationConfig.put("maxOutputTokens", request.getMaxTokens());
        }
        body.put("generationConfig", generationConfig);

        // tools & tool_config（函数调用配置）
        List<Map<String, Object>> tools = buildTools(request);
        if (!tools.isEmpty()) {
            body.put("tools", tools);
            // 默认强制工具调用；可被下方 mutator 覆盖
            Map<String, Object> toolConfig = new HashMap<>();
            Map<String, Object> functionCalling = new HashMap<>();
            functionCalling.put("mode", "ANY");
            functionCalling.put("allowedFunctionNames", collectAllowedFunctionNames(tools));
            toolConfig.put("functionCalling", functionCalling);
            body.put("toolConfig", toolConfig);
        }
        // 扩展点：允许外部/上层通过 parameters 定制请求（思考、缓存、MCP 等）
        for (RequestBodyMutator mutator : requestMutators) {
            try { mutator.mutate(body, request); } catch (Exception ignore) {}
        }
        return body;
    }

    private List<Map<String, Object>> convertMessages(AIRequest request) {
        List<Map<String, Object>> contents = new ArrayList<>();

        if (request.getPrompt() != null && !request.getPrompt().isEmpty()) {
            Map<String, Object> sys = new HashMap<>();
            List<Map<String, Object>> parts = new ArrayList<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", request.getPrompt());
            parts.add(part);
            sys.put("role", "user"); // Gemini不直接支持system，这里以user传入角色指令
            sys.put("parts", parts);
            contents.add(sys);
        }

        if (request.getMessages() != null) {
            for (AIRequest.Message m : request.getMessages()) {
                Map<String, Object> msg = new HashMap<>();
                List<Map<String, Object>> parts = new ArrayList<>();
                String role = m.getRole() == null ? "user" : m.getRole().toLowerCase();

                if ("tool".equals(role) && m.getToolExecutionResult() != null) {
                    // 将工具执行结果映射为 Gemini functionResponse 部分
                    msg.put("role", "tool");
                    Map<String, Object> fr = new HashMap<>();
                    Map<String, Object> functionResponse = new HashMap<>();
                    functionResponse.put("name", m.getToolExecutionResult().getToolName());
                    // 尝试将结果解析为JSON对象，否则作为字符串返回
                    Object responseObject = tryParseJson(m.getToolExecutionResult().getResult());
                    functionResponse.put("response", responseObject);
                    fr.put("functionResponse", functionResponse);
                    parts.add(fr);
                } else {
                    // 普通文本消息
                    Map<String, Object> part = new HashMap<>();
                    part.put("text", m.getContent());
                    parts.add(part);

                    switch (role) {
                        case "assistant":
                            msg.put("role", "model");
                            break;
                        case "system":
                            msg.put("role", "user");
                            break;
                        default:
                            msg.put("role", "user");
                    }
                }

                msg.put("parts", parts);
                contents.add(msg);
            }
        }

        // 附件（图片/文件）支持：通过 parameters.attachments 传入，全局追加为一条用户消息
        List<Map<String, Object>> attachmentParts = buildAttachmentPartsFromParameters(request);
        if (!attachmentParts.isEmpty()) {
            Map<String, Object> attachMsg = new HashMap<>();
            attachMsg.put("role", "user");
            attachMsg.put("parts", attachmentParts);
            contents.add(attachMsg);
        }

        return contents;
    }

    private Object tryParseJson(String text) {
        if (text == null || text.isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(text, Map.class);
        } catch (Exception ignore) {
            return Map.of("text", text);
        }
    }

    // === 扩展：默认 Mutators ===
    private void registerDefaultMutators() {
        // 1) 函数调用配置覆盖（parameters.function_calling / functionCalling）
        requestMutators.add((body, request) -> {
            Map<String, Object> params = request.getParameters();
            if (params == null) return;
            Object fc = params.getOrDefault("function_calling", params.get("functionCalling"));
            if (!(fc instanceof Map<?, ?> cfg)) return;
            Map<String, Object> functionCalling = new HashMap<>();
            for (Map.Entry<?, ?> e : cfg.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    functionCalling.put(e.getKey().toString(), e.getValue());
                }
            }
            Object tcObj = body.get("toolConfig");
            Map<String, Object> toolConfig;
            if (tcObj instanceof Map<?, ?> tc) {
                toolConfig = new HashMap<>();
                for (Map.Entry<?, ?> e : tc.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        toolConfig.put(e.getKey().toString(), e.getValue());
                    }
                }
            } else {
                toolConfig = new HashMap<>();
            }
            toolConfig.put("functionCalling", functionCalling);
            body.put("toolConfig", toolConfig);
        });

        // 2) 上下文缓存（parameters.context.cacheId / prepend / persist）
        requestMutators.add((body, request) -> {
            Map<String, Object> params = request.getParameters();
            if (params == null) return;
            Object ctxObj = params.get("context");
            if (!(ctxObj instanceof Map<?, ?> ctx)) return;
            String cacheId = asString(ctx.get("cacheId"));
            boolean prepend = asBoolean(ctx.get("prepend"), true);
            boolean persist = asBoolean(ctx.get("persist"), false);
            if (cacheId == null || cacheId.isEmpty()) return;

            Object contentsObj = body.get("contents");
            if (!(contentsObj instanceof List<?> list)) return;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contents = (List<Map<String, Object>>) (List<?>) list;
            if (prepend) {
                List<Map<String, Object>> cached = CONTEXT_CACHE.get(cacheId);
                if (cached != null && !cached.isEmpty()) {
                    Map<String, Object> cachedMsg = new HashMap<>();
                    cachedMsg.put("role", "user");
                    cachedMsg.put("parts", cached);
                    contents.add(0, cachedMsg);
                }
            }
            if (persist) {
                List<Map<String, Object>> persistParts = new ArrayList<>();
                persistParts.addAll(buildAttachmentPartsFromParameters(request));
                if (request.getPrompt() != null && !request.getPrompt().isEmpty()) {
                    Map<String, Object> p = new HashMap<>();
                    p.put("text", request.getPrompt());
                    persistParts.add(p);
                }
                if (!persistParts.isEmpty()) {
                    CONTEXT_CACHE.put(cacheId, persistParts);
                }
            }
        });

        // 3) MCP 工具桥（parameters.mcpTools: 与工具声明合并）
        requestMutators.add((body, request) -> {
            Map<String, Object> params = request.getParameters();
            if (params == null) return;
            Object mcp = params.get("mcpTools");
            if (!(mcp instanceof List<?> list) || list.isEmpty()) return;
            Object toolsObj = body.get("tools");
            List<Map<String, Object>> tools;
            if (toolsObj instanceof List<?> tlist) {
                tools = new ArrayList<>();
                for (Object t : tlist) {
                    if (t instanceof Map<?, ?> tm) {
                        Map<String, Object> copy = new HashMap<>();
                        for (Map.Entry<?, ?> e : tm.entrySet()) {
                            if (e.getKey() != null && e.getValue() != null) {
                                copy.put(e.getKey().toString(), e.getValue());
                            }
                        }
                        tools.add(copy);
                    }
                }
            } else {
                tools = new ArrayList<>();
            }

            List<Map<String, Object>> fns = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> decl = new HashMap<>();
                    Object name = m.get("name");
                    if (name != null) decl.put("name", name.toString());
                    Object desc = m.get("description");
                    if (desc != null) decl.put("description", desc.toString());
                    Object parameters = m.get("parameters");
                    if (parameters instanceof Map<?, ?> pm) {
                        Map<String, Object> schema = new HashMap<>();
                        for (Map.Entry<?, ?> e : pm.entrySet()) {
                            if (e.getKey() != null && e.getValue() != null) {
                                schema.put(e.getKey().toString(), e.getValue());
                            }
                        }
                        decl.put("parameters", schema);
                    } else {
                        Map<String, Object> paramsSchema = new HashMap<>();
                        paramsSchema.put("type", "object");
                        paramsSchema.put("additionalProperties", true);
                        decl.put("parameters", paramsSchema);
                    }
                    fns.add(decl);
                }
            }
            if (!fns.isEmpty()) {
                Map<String, Object> tool = new HashMap<>();
                tool.put("function_declarations", fns);
                tools.add(tool);
                body.put("tools", tools);
            }
        });

        // 4) 思考/推理（parameters.reasoning: Map）→ generationConfig.reasoning 直传
        requestMutators.add((body, request) -> {
            Map<String, Object> params = request.getParameters();
            if (params == null) return;
            Object r = params.get("reasoning");
            if (!(r instanceof Map<?, ?> rm)) return;
            Object gcObj = body.get("generationConfig");
            Map<String, Object> generationConfig;
            if (gcObj instanceof Map<?, ?> gc) {
                generationConfig = new HashMap<>();
                for (Map.Entry<?, ?> e : gc.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        generationConfig.put(e.getKey().toString(), e.getValue());
                    }
                }
            } else {
                generationConfig = new HashMap<>();
            }
            Map<String, Object> reasoning = new HashMap<>();
            for (Map.Entry<?, ?> e : rm.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    reasoning.put(e.getKey().toString(), e.getValue());
                }
            }
            generationConfig.put("reasoning", reasoning);
            body.put("generationConfig", generationConfig);
        });

        // 5) 会话/系统指令（parameters.session.systemInstruction）→ system_instruction（Content结构）
        requestMutators.add((body, request) -> {
            Map<String, Object> params = request.getParameters();
            if (params == null) return;
            Object sObj = params.get("session");
            if (!(sObj instanceof Map<?, ?> s)) return;
            Object sys = s.get("systemInstruction");
            if (sys == null) return;
            Map<String, Object> sysInst = new HashMap<>();
            List<Map<String, Object>> parts = new ArrayList<>();
            if (sys instanceof String str) {
                Map<String, Object> p = new HashMap<>();
                p.put("text", str);
                parts.add(p);
            } else if (sys instanceof Map<?, ?> sm) {
                for (Map.Entry<?, ?> e : sm.entrySet()) {
                    String k = e.getKey() == null ? null : e.getKey().toString();
                    Object v = e.getValue();
                    if (k == null || v == null) continue;
                    if ("text".equals(k)) {
                        Map<String, Object> p = new HashMap<>(); p.put("text", v.toString()); parts.add(p);
                    } else if ("fileUri".equals(k)) {
                        Map<String, Object> file = new HashMap<>();
                        Map<String, Object> fileData = new HashMap<>();
                        fileData.put("fileUri", v.toString());
                        file.put("fileData", fileData);
                        parts.add(file);
                    } else if ("inlineData".equals(k) && v instanceof Map<?, ?> id) {
                        Map<String, Object> inline = new HashMap<>();
                        Map<String, Object> inlineData = new HashMap<>();
                        for (Map.Entry<?, ?> ee : id.entrySet()) {
                            if (ee.getKey() != null && ee.getValue() != null) {
                                inlineData.put(ee.getKey().toString(), ee.getValue());
                            }
                        }
                        inline.put("inlineData", inlineData);
                        parts.add(inline);
                    }
                }
            }
            if (!parts.isEmpty()) {
                sysInst.put("role", "user");
                sysInst.put("parts", parts);
                body.put("systemInstruction", sysInst);
            }
        });

        // 6) 官方文件API自动上传（parameters.officialFileApi.autoUpload = true）
        requestMutators.add((body, request) -> {
            Map<String, Object> params = request.getParameters();
            if (params == null) return;
            Object ofa = params.get("officialFileApi");
            if (!(ofa instanceof Map<?, ?> cfg)) return;
            Object auto = cfg.get("autoUpload");
            boolean autoUpload = asBoolean(auto, false);
            if (!autoUpload) return;

            Object contentsObj = body.get("contents");
            if (!(contentsObj instanceof List<?> messages)) return;
            for (Object m : messages) {
                if (!(m instanceof Map<?, ?> msg)) continue;
                Object partsObj = msg.get("parts");
                if (!(partsObj instanceof List<?> parts)) continue;
                for (Object p : parts) {
                    if (!(p instanceof Map<?, ?> part)) continue;
                    Object inline = part.get("inlineData");
                    if (inline instanceof Map<?, ?> inlineData) {
                        String mime = asString(inlineData.get("mimeType"));
                        String data = asString(inlineData.get("data"));
                        String uri = tryUploadFileReturnUri(mime, data);
                        if (uri != null) {
                            // 替换为 fileData
                            Map<String, Object> file = new HashMap<>();
                            Map<String, Object> fileData = new HashMap<>();
                            if (mime != null) fileData.put("mimeType", mime);
                            fileData.put("fileUri", uri);
                            file.put("fileData", fileData);
                            // 更新part
                            @SuppressWarnings("unchecked")
                            Map<String, Object> partMap = (Map<String, Object>) (Map<?, ?>) part;
                            partMap.remove("inlineData");
                            partMap.putAll(file);
                        }
                    }
                }
            }
        });
    }

    private boolean asBoolean(Object o, boolean dft) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return Boolean.parseBoolean(s);
        return dft;
    }
    private String asString(Object o) { return o == null ? null : o.toString(); }

    private List<Map<String, Object>> buildAttachmentPartsFromParameters(AIRequest request) {
        List<Map<String, Object>> parts = new ArrayList<>();
        Map<String, Object> params = request.getParameters();
        if (params == null) return parts;
        Object att = params.get("attachments");
        if (!(att instanceof List<?> list) || list.isEmpty()) return parts;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String type = asString(m.get("type"));
            String mimeType = asString(m.get("mimeType"));
            if ("image_base64".equalsIgnoreCase(type)) {
                String data = asString(m.get("data"));
                if (data != null) {
                    Map<String, Object> inline = new HashMap<>();
                    Map<String, Object> inlineData = new HashMap<>();
                    inlineData.put("mimeType", mimeType != null ? mimeType : "image/png");
                    inlineData.put("data", data);
                    inline.put("inlineData", inlineData);
                    parts.add(inline);
                }
            } else if ("file_uri".equalsIgnoreCase(type)) {
                String fileUri = asString(m.get("fileUri"));
                if (fileUri != null) {
                    Map<String, Object> file = new HashMap<>();
                    Map<String, Object> fileData = new HashMap<>();
                    if (mimeType != null) fileData.put("mimeType", mimeType);
                    fileData.put("fileUri", fileUri);
                    file.put("fileData", fileData);
                    parts.add(file);
                }
            } else if ("text".equalsIgnoreCase(type)) {
                String text = asString(m.get("text"));
                if (text != null) {
                    Map<String, Object> p = new HashMap<>();
                    p.put("text", text);
                    parts.add(p);
                }
            }
        }
        return parts;
    }

    // 对外公开注册扩展点
    public void registerMutator(RequestBodyMutator mutator) {
        if (mutator != null) {
            this.requestMutators.add(mutator);
        }
    }

    // === 官方文件API上传（最简实现，失败则返回null以回退为 inline_data） ===
    private String tryUploadFileReturnUri(String mimeType, String base64Data) {
        if (base64Data == null || base64Data.isEmpty()) return null;
        try {
            Map<String, Object> payload = new HashMap<>();
            Map<String, Object> file = new HashMap<>();
            if (mimeType != null) file.put("mimeType", mimeType);
            file.put("data", base64Data);
            payload.put("file", file);

            String resp = webClient.post()
                    .uri("/files:upload?key={apiKey}", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (resp == null) return null;
            Map<?, ?> json = objectMapper.readValue(resp, Map.class);
            Object f = json.get("file");
            if (f instanceof Map<?, ?> fm) {
                Object uri = fm.get("uri");
                return uri == null ? null : uri.toString();
            }
        } catch (Exception e) {
            log.warn("官方文件API上传失败，将回退为inline_data: {}", e.getMessage());
        }
        return null;
    }

    private List<Map<String, Object>> buildTools(AIRequest request) {
        List<Map<String, Object>> tools = new ArrayList<>();
        if (request.getToolSpecifications() == null || request.getToolSpecifications().isEmpty()) {
            return tools;
        }

        List<Map<String, Object>> functionDeclarations = new ArrayList<>();

        int index = 0;
        for (Object spec : request.getToolSpecifications()) {
            try {
                String name = tryGetString(spec, "name", "getName");
                if (name == null || name.isEmpty()) {
                    name = "tool_" + (++index);
                }
                String description = tryGetString(spec, "description", "getDescription");

                Map<String, Object> decl = new HashMap<>();
                decl.put("name", name);
                if (description != null) {
                    decl.put("description", description);
                }

                // 最小参数schema，放宽为任意对象，避免上游schema差异导致失败
                Map<String, Object> params = new HashMap<>();
                params.put("type", "object");
                params.put("additionalProperties", true);
                decl.put("parameters", params);

                functionDeclarations.add(decl);
            } catch (Exception ignore) {
                // 忽略单个工具声明错误
            }
        }

        if (!functionDeclarations.isEmpty()) {
            Map<String, Object> tool = new HashMap<>();
            tool.put("function_declarations", functionDeclarations);
            tools.add(tool);
        }
        return tools;
    }

    private List<String> collectAllowedFunctionNames(List<Map<String, Object>> tools) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> tool : tools) {
            Object f = tool.get("function_declarations");
            if (f instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        Object n = m.get("name");
                        if (n != null) names.add(n.toString());
                    }
                }
            }
        }
        return names;
    }

    private String tryGetString(Object obj, String... methodNames) {
        for (String m : methodNames) {
            try {
                var method = obj.getClass().getMethod(m);
                Object val = method.invoke(obj);
                if (val != null) return val.toString();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String extractFirstText(GenAiResponse resp) {
        if (resp == null || resp.getCandidates() == null || resp.getCandidates().isEmpty()) return "";
        GenAiResponse.Candidate c = resp.getCandidates().get(0);
        if (c.getContent() == null || c.getContent().getParts() == null) return "";
        for (GenAiResponse.Part p : c.getContent().getParts()) {
            if (p.getText() != null) return p.getText();
        }
        return "";
    }

    private AIResponse convertToAIResponse(GenAiResponse resp, AIRequest request) {
        AIResponse ai = createBaseResponse("", request);

        // 内容
        String content = extractFirstText(resp);
        ai.setContent(content != null ? content : "");

        // 完成原因
        if (resp != null && resp.getCandidates() != null && !resp.getCandidates().isEmpty()) {
            String fr = resp.getCandidates().get(0).getFinishReason();
            if (fr != null) ai.setFinishReason(fr);
        }

        // 函数调用 → 工具调用
        List<AIResponse.ToolCall> toolCalls = new ArrayList<>();
        if (resp != null && resp.getCandidates() != null) {
            for (GenAiResponse.Candidate c : resp.getCandidates()) {
                if (c.getContent() == null || c.getContent().getParts() == null) continue;
                for (GenAiResponse.Part p : c.getContent().getParts()) {
                    if (p.getFunctionCall() != null && p.getFunctionCall().getName() != null) {
                        String name = p.getFunctionCall().getName();
                        String argsJson;
                        try {
                            argsJson = objectMapper.writeValueAsString(p.getFunctionCall().getArgs());
                        } catch (Exception e) {
                            argsJson = "{}";
                        }
                        AIResponse.ToolCall call = AIResponse.ToolCall.builder()
                                .id(name + "-" + System.currentTimeMillis())
                                .type("function")
                                .function(
                                        AIResponse.Function.builder()
                                                .name(name)
                                                .arguments(argsJson)
                                                .build()
                                )
                                .build();
                        toolCalls.add(call);
                    }
                }
            }
        }
        if (!toolCalls.isEmpty()) {
            ai.setToolCalls(toolCalls);
        }

        // token 使用（如果提供）
        if (resp != null && resp.getUsage() != null) {
            AIResponse.TokenUsage u = new AIResponse.TokenUsage();
            u.setPromptTokens(resp.getUsage().getPromptTokenCount());
            u.setCompletionTokens(resp.getUsage().getCandidatesTokenCount());
            ai.setTokenUsage(u);
        }

        return ai;
    }

    // === GenAI 响应模型（对齐 REST 结构） ===
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GenAiResponse {
        private List<Candidate> candidates;
        private Usage usage;

        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Candidate {
            private Content content;
            @JsonProperty("finishReason")
            private String finishReason;
        }

        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Content {
            private List<Part> parts;
            private String role;
        }

        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Part {
            private String text;
            @JsonProperty("functionCall")
            private FunctionCall functionCall;
        }

        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class FunctionCall {
            private String name;
            private Map<String, Object> args;
        }

        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Usage {
            @JsonProperty("promptTokenCount")
            private int promptTokenCount;
            @JsonProperty("candidatesTokenCount")
            private int candidatesTokenCount;
            @JsonProperty("totalTokenCount")
            private int totalTokenCount;
        }
    }
}


