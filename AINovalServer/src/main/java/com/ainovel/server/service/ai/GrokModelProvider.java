package com.ainovel.server.service.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.ainovel.server.domain.model.AIRequest;
import com.ainovel.server.domain.model.AIResponse;
import com.ainovel.server.domain.model.ModelInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

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

import com.ainovel.server.config.ProxyConfig;

/**
 * X.AI的Grok模型提供商
 */
@Slf4j
public class GrokModelProvider extends AbstractAIModelProvider {

    private static final String DEFAULT_API_ENDPOINT = "https://api.x.ai/v1";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private WebClient webClient;
    private ProxyConfig proxyConfig;
    
    // 添加模型价格映射
    private static final Map<String, Double> TOKEN_PRICES;

    static {
        Map<String, Double> prices = new HashMap<>();
        // 输入价格 (每1K tokens USD)
        prices.put("x-ai/grok-3-beta-input", 0.003);
        prices.put("x-ai/grok-3-input", 0.003);
        prices.put("x-ai/grok-3-fast-input", 0.0015);
        prices.put("x-ai/grok-3-mini-input", 0.0006);
        prices.put("x-ai/grok-3-mini-fast-input", 0.0003);
        prices.put("x-ai/grok-2-vision-1212-input", 0.003);
        
        // 输出价格 (每1K tokens USD)
        prices.put("x-ai/grok-3-beta-output", 0.006);
        prices.put("x-ai/grok-3-output", 0.006);
        prices.put("x-ai/grok-3-fast-output", 0.003);
        prices.put("x-ai/grok-3-mini-output", 0.0012);
        prices.put("x-ai/grok-3-mini-fast-output", 0.0006);
        prices.put("x-ai/grok-2-vision-1212-output", 0.006);
        
        TOKEN_PRICES = Collections.unmodifiableMap(prices);
    }
    
    /**
     * 构造函数
     * @param modelName 模型名称（x-ai/grok-3-beta）
     * @param apiKey API密钥
     * @param apiEndpoint API端点
     */
    public GrokModelProvider(String modelName, String apiKey, String apiEndpoint) {
        super("x-ai", modelName, apiKey, apiEndpoint);
        initWebClient();
    }
    
    /**
     * 构造函数（带代理配置）
     * @param modelName 模型名称
     * @param apiKey API密钥
     * @param apiEndpoint API端点
     * @param proxyConfig 代理配置
     */
    public GrokModelProvider(String modelName, String apiKey, String apiEndpoint, ProxyConfig proxyConfig) {
        super("x-ai", modelName, apiKey, apiEndpoint);
        this.proxyConfig = proxyConfig;
        this.proxyEnabled = (proxyConfig != null && proxyConfig.isEnabled());
        if (proxyEnabled) {
            this.proxyHost = proxyConfig.getHost();
            this.proxyPort = proxyConfig.getPort();
            this.trustAllCerts = proxyConfig.isTrustAllCerts();
        }
        initWebClient();
    }
    
    /**
     * 初始化WebClient
     */
    private void initWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(120)) // 设置响应超时
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000); // 设置连接超时
        
        if (proxyEnabled) {
            try {
                // 先检查是否有ProxyConfig
                if (proxyConfig != null && proxyConfig.isEnabled()) {
                    this.proxyHost = proxyConfig.getHost();
                    this.proxyPort = proxyConfig.getPort();
                    log.info("Grok Provider: 从ProxyConfig获取代理配置: Host={}, Port={}", 
                            this.proxyHost, this.proxyPort);
                }
                
                // 配置HTTP客户端
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
                    log.warn("已启用 trustAllCerts（仅建议用于排障），生产请关闭！");
                }
                
                log.info("Grok Provider: 已启用代理: {}:{}", proxyHost, proxyPort);
            } catch (Exception e) {
                log.error("Grok Provider: 配置代理时出错: {}", e.getMessage(), e);
            }
        }
        
        // 获取API端点
        String baseUrl = getApiEndpoint(DEFAULT_API_ENDPOINT);
        
        // 如果URL不包含/v1，确保添加版本路径
        if (!baseUrl.endsWith("/v1")) {
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl + "v1";
            } else {
                baseUrl = baseUrl + "/v1";
            }
        }
        
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        
        log.info("Grok Provider: WebClient已初始化，基础URL: {}", baseUrl);
    }
    
    /**
     * 重新初始化WebClient（代理配置变更后调用）
     */
    public void refreshWebClient() {
        initWebClient();
    }
    
    @Override
    public void setProxy(String host, int port) {
        super.setProxy(host, port);
        refreshWebClient();
    }
    
    @Override
    public void disableProxy() {
        super.disableProxy();
        refreshWebClient();
    }

    @Override
    public Mono<AIResponse> generateContent(AIRequest request) {
        if (isApiKeyEmpty()) {
            AIResponse errorResponse = createBaseResponse("API密钥未配置", request);
            errorResponse.setFinishReason("error");
            return Mono.just(errorResponse);
        }

        try {
            // 构建请求体
            Map<String, Object> requestBody = createRequestBody(request, false);
            log.info("开始X.AI非流式请求, 模型: {}, 请求体: {}", modelName, requestBody);
            
            // 调用API
            return webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(responseJson -> {
                        try {
                            log.debug("X.AI API响应: {}", responseJson);
                            GrokResponse grokResponse = objectMapper.readValue(responseJson, GrokResponse.class);
                            return convertToAIResponse(grokResponse, request);
                        } catch (Exception e) {
                            log.error("解析Grok响应失败: {}", e.getMessage(), e);
                            AIResponse errorResponse = createBaseResponse("解析响应失败: " + e.getMessage(), request);
                            errorResponse.setFinishReason("error");
                            return errorResponse;
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("Grok API调用失败: {}", getErrorDetails(e));
                        return handleApiException(e, request);
                    });
        } catch (Exception e) {
            log.error("Grok API调用失败: {}", e.getMessage(), e);
            return handleApiException(e, request);
        }
    }

    @Override
    public Flux<String> generateContentStream(AIRequest request) {
        if (isApiKeyEmpty()) {
            return Flux.just("错误：API密钥未配置");
        }

        // 创建Sink用于流式输出，支持暂停
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        
        // 记录请求开始时间，用于问题诊断
        final long requestStartTime = System.currentTimeMillis();
        final AtomicLong firstChunkTime = new AtomicLong(0);
        
        // 标记是否已经收到了任何内容
        final AtomicBoolean hasReceivedContent = new AtomicBoolean(false);
        
        try {
            // 构建请求体
            Map<String, Object> requestBody = createRequestBody(request, true);
            log.info("开始X.AI流式请求, 模型: {}, 请求体: {}", modelName, requestBody);
            
            // 调用流式API
            webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .subscribe(
                        chunk -> {
                            try {
                                // 记录首个响应到达时间
                                if (firstChunkTime.get() == 0) {
                                    firstChunkTime.set(System.currentTimeMillis());
                                    hasReceivedContent.set(true);
                                    log.info("Grok: 收到首个响应, 耗时: {}ms, 模型: {}, 内容: {}",
                                            firstChunkTime.get() - requestStartTime, modelName, chunk);
                                } else {
                                    //log.debug("Grok: 收到流式响应块: {}", chunk);
                                }
                                
                                // 解析流式响应
                                if (chunk.startsWith("data: ")) {
                                    chunk = chunk.substring(6);
                                }
                                
                                if ("[DONE]".equals(chunk) || chunk.isEmpty()) {
                                    log.debug("收到流式结束标志 [DONE] 或空内容");
                                    return;
                                }
                                
                                GrokResponse grokResponse = objectMapper.readValue(chunk, GrokResponse.class);
                                if (grokResponse.getChoices() != null && !grokResponse.getChoices().isEmpty()) {
                                    GrokResponse.Choice choice = grokResponse.getChoices().get(0);
                                    if (choice.getDelta() != null) {
                                        String content = choice.getDelta().getContent();
                                        if (content != null) {
                                            //log.debug("解析到内容片段: {}", content);
                                            sink.tryEmitNext(content);
                                        } else {
                                            //log.debug("解析到空内容片段, delta: {}", choice.getDelta());
                                        }
                                    } else {
                                        log.debug("选择项没有delta字段: {}", choice);
                                    }
                                } else {
                                    log.debug("响应没有choices字段或为空: {}", grokResponse);
                                }
                            } catch (Exception e) {
                                log.error("解析Grok流式响应失败: {}", e.getMessage(), e);
                                sink.tryEmitNext("错误：" + e.getMessage());
                            }
                        },
                        error -> {
                            log.error("Grok流式API调用失败: {}", getErrorDetails(error));
                            sink.tryEmitNext("错误：" + error.getMessage());
                            sink.tryEmitComplete();
                        },
                        () -> {
                            log.info("Grok流式生成完成，总耗时: {}ms", System.currentTimeMillis() - requestStartTime);
                            sink.tryEmitComplete();
                        }
                    );
            
            // 创建一个完成信号 - 用于控制心跳流的结束
            final Sinks.One<Boolean> completionSignal = Sinks.one();
            
            // 主内容流
            Flux<String> mainStream = sink.asFlux()
                    // 添加延迟重试，避免网络抖动导致请求失败
                    .retryWhen(Retry.backoff(1, Duration.ofSeconds(2))
                            .filter(error -> {
                                // 只对网络错误或超时错误进行重试
                                boolean isNetworkError = error instanceof java.net.SocketException
                                        || error instanceof java.io.IOException
                                        || error instanceof java.util.concurrent.TimeoutException;
                                if (isNetworkError) {
                                    log.warn("Grok流式生成遇到网络错误，将进行重试: {}", error.getMessage());
                                }
                                return isNetworkError;
                            })
                    )
                    .timeout(Duration.ofSeconds(300)) // 增加超时时间到300秒，避免大模型生成时间过长导致中断
                    .doOnComplete(() -> {
                        // 发出完成信号，通知心跳流停止
                        completionSignal.tryEmitValue(true);
                        log.debug("Grok主流完成，已发送停止心跳信号");
                    })
                    .doOnCancel(() -> {
                        // 取消时如果已经收到内容，不要关闭sink
                        if (!hasReceivedContent.get()) {
                            // 只有在没有收到任何内容时才完成sink
                            log.debug("Grok主流取消，但未收到任何响应，发送停止心跳信号");
                            completionSignal.tryEmitValue(true);
                        } else {
                            log.debug("Grok主流取消，但已收到内容，保持sink开放以接收后续内容");
                        }
                    })
                    .doOnError(error -> {
                        // 错误时也发出完成信号
                        completionSignal.tryEmitValue(true);
                        log.debug("Grok主流出错，已发送停止心跳信号: {}", error.getMessage());
                    });

            // 心跳流，当completionSignal发出时停止
            Flux<String> heartbeatStream = Flux.interval(Duration.ofSeconds(15))
                    .map(tick -> {
                        log.debug("发送Grok心跳信号 #{}", tick);
                        return "heartbeat";
                    })
                    // 使用takeUntil操作符，当completionSignal发出值时停止心跳
                    .takeUntilOther(completionSignal.asMono());

            // 合并主流和心跳流
            return Flux.merge(mainStream, heartbeatStream)
                    .onErrorResume(e -> {
                        log.error("Grok流式生成内容时出错: {}", e.getMessage(), e);
                        return Flux.just("错误：" + e.getMessage());
                    });
                    
        } catch (Exception e) {
            log.error("Grok流式API调用失败", e);
            return Flux.just("错误：" + e.getMessage());
        }
    }

    @Override
    public Mono<Double> estimateCost(AIRequest request) {
        // 获取模型输入价格（每1000个令牌的美元价格）
        String inputPriceKey = modelName + "-input";
        String outputPriceKey = modelName + "-output";
        
        double inputPricePerThousandTokens = TOKEN_PRICES.getOrDefault(inputPriceKey, 0.003);
        double outputPricePerThousandTokens = TOKEN_PRICES.getOrDefault(outputPriceKey, 0.006);
        
        // 估算输入令牌数
        int inputTokens = estimateInputTokens(request);
        
        // 估算输出令牌数
        int outputTokens = request.getMaxTokens() != null ? request.getMaxTokens() : 1000;
        
        // 计算成本（美元）
        double costInUSD = (inputTokens / 1000.0) * inputPricePerThousandTokens
                + (outputTokens / 1000.0) * outputPricePerThousandTokens;
        
        // 转换为人民币（假设汇率为7.2）
        double costInCNY = costInUSD * 7.2;
        
        return Mono.just(costInCNY);
    }

    @Override
    public Mono<Boolean> validateApiKey() {
        if (isApiKeyEmpty()) {
            return Mono.just(false);
        }

        // 处理模型名称，确保正确的格式
        final String apiModel = modelName.startsWith("x-ai/") ? 
                modelName.substring(5) : // 去掉"x-ai/"前缀
                modelName;

        // 创建一个简单的测试请求
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", apiModel);
        requestBody.put("messages", List.of(Map.of("role", "user", "content", "Hello")));
        requestBody.put("max_tokens", 5);
        
        log.info("开始验证X.AI API密钥, 请求URL: {}/chat/completions, 模型: {}, 原始模型名: {}, 请求体: {}", 
                getApiEndpoint(DEFAULT_API_ENDPOINT), apiModel, modelName, requestBody);
        
        try {
            // 尝试通过模型列表API验证密钥，可能更可靠
            String baseUrl = getApiEndpoint(DEFAULT_API_ENDPOINT);
            log.info("尝试通过模型列表API验证密钥: {}/models", baseUrl);
            
            return webClient.get()
                    .uri("/models")
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnNext(response -> {
                        log.info("X.AI模型列表API响应成功, 长度: {}", response.length());
                        log.debug("X.AI模型列表API响应: {}", response);
                        
                        // 检查响应中是否包含当前模型名称
                        if (!response.contains(apiModel)) {
                            log.warn("X.AI模型列表响应中未找到当前模型: {}, 可能需要检查模型名称格式", apiModel);
                        }
                    })
                    .map(response -> true)
                    .onErrorResume(e -> {
                        log.error("验证X.AI API密钥(模型列表)失败: {}", getErrorDetails(e));
                        
                        log.info("尝试通过chat/completions API验证密钥，使用模型: {}", apiModel);
                        
                        // 如果模型列表API失败，尝试chat/completions API
                        return webClient.post()
                                .uri("/chat/completions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Bearer " + apiKey)
                                .bodyValue(requestBody)
                                .retrieve()
                                .bodyToMono(String.class)
                                .doOnNext(response -> {
                                    log.info("X.AI chat/completions API响应成功, 长度: {}", response.length());
                                    log.debug("X.AI chat/completions API响应内容: {}", response);
                                })
                                .map(response -> true)
                                .onErrorResume(chatError -> {
                                    log.error("验证X.AI API密钥(chat/completions)失败: {}", getErrorDetails(chatError));
                                    
                                    // 如果含有x-ai前缀的模型名失败，尝试不带前缀的模型名
                                    if (modelName.startsWith("x-ai/") && chatError.getMessage().contains("model")) {
                                        String altModel = modelName.substring(5); // 去掉"x-ai/"前缀
                                        log.info("尝试使用替代模型名称: {} 进行重试", altModel);
                                        
                                        Map<String, Object> altRequestBody = new HashMap<>(requestBody);
                                        altRequestBody.put("model", altModel);
                                        
                                        return webClient.post()
                                                .uri("/chat/completions")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .header("Authorization", "Bearer " + apiKey)
                                                .bodyValue(altRequestBody)
                                                .retrieve()
                                                .bodyToMono(String.class)
                                                .map(resp -> true)
                                                .onErrorResume(altError -> {
                                                    log.error("使用替代模型名称验证失败: {}", getErrorDetails(altError));
                                                    return Mono.just(false);
                                                });
                                    }
                                    
                                    // 如果不包含x-ai前缀，尝试添加前缀
                                    if (!modelName.startsWith("x-ai/") && chatError.getMessage().contains("model")) {
                                        // 获取基本模型名，如果是有前缀的模型名，保留基本名称部分
                                        String baseModelName = modelName;
                                        if (modelName.contains("/")) {
                                            baseModelName = modelName.substring(modelName.indexOf("/") + 1);
                                        }
                                        
                                        log.info("尝试使用基本模型名称: {} 进行重试", baseModelName);
                                        
                                        Map<String, Object> baseRequestBody = new HashMap<>(requestBody);
                                        baseRequestBody.put("model", baseModelName);
                                        
                                        return webClient.post()
                                                .uri("/chat/completions")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .header("Authorization", "Bearer " + apiKey)
                                                .bodyValue(baseRequestBody)
                                                .retrieve()
                                                .bodyToMono(String.class)
                                                .map(resp -> true)
                                                .onErrorResume(baseError -> {
                                                    log.error("使用基本模型名称验证失败: {}", getErrorDetails(baseError));
                                                    return Mono.just(false);
                                                });
                                    }
                                    
                                    return Mono.just(false);
                                });
                    });
        } catch (Exception e) {
            log.error("验证X.AI API密钥时发生异常: {}", e.getMessage(), e);
            return Mono.just(false);
        }
    }
    
    /**
     * 获取详细的错误信息
     * @param error 错误对象
     * @return 格式化的错误信息
     */
    private String getErrorDetails(Throwable error) {
        StringBuilder details = new StringBuilder();
        details.append(error.getMessage());
        
        // 检查是否为WebClient错误并包含响应信息
        if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
            org.springframework.web.reactive.function.client.WebClientResponseException wcError = 
                    (org.springframework.web.reactive.function.client.WebClientResponseException) error;
            
            details.append("\nHTTP状态码: ").append(wcError.getStatusCode());
            details.append("\n请求URL: ").append(wcError.getRequest() != null ? 
                    wcError.getRequest().getURI() : "未知");
            details.append("\n请求方法: ").append(wcError.getRequest() != null ? 
                    wcError.getRequest().getMethod() : "未知");
            details.append("\n响应头: ").append(wcError.getHeaders());
            
            // 尝试添加响应体
            if (wcError.getResponseBodyAsString() != null && !wcError.getResponseBodyAsString().isEmpty()) {
                details.append("\n响应体: ").append(wcError.getResponseBodyAsString());
            }
        }
        
        return details.toString();
    }
    
    @Override
    public Flux<ModelInfo> listModels() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Flux.error(new RuntimeException("API密钥不能为空"));
        }
        
        try {
            // 获取API端点
            String baseUrl = apiEndpoint != null && !apiEndpoint.trim().isEmpty() ?
                    apiEndpoint : DEFAULT_API_ENDPOINT;
            
            // 创建WebClient
            WebClient tempWebClient = WebClient.builder()
                    .baseUrl(baseUrl)
                    .build();
            
            // 调用X.AI API获取模型列表
            return tempWebClient.get()
                    .uri("/models")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .flatMapMany(response -> {
                        try {
                            // 解析响应
                            log.debug("X.AI模型列表响应: {}", response);
                            
                            // 实际情况可能需要根据API响应格式进行调整
                            // 这里简化处理，直接返回预定义的模型列表
                            return Flux.fromIterable(getDefaultXAIModels());
                        } catch (Exception e) {
                            log.error("解析X.AI模型列表时出错", e);
                            return Flux.fromIterable(getDefaultXAIModels());
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("获取X.AI模型列表时出错: {}", e.getMessage(), e);
                        // 出错时返回预定义的模型列表
                        return Flux.fromIterable(getDefaultXAIModels());
                    });
        } catch (Exception e) {
            log.error("调用X.AI API时出错", e);
            return Flux.fromIterable(getDefaultXAIModels());
        }
    }

    @Override
    public Flux<ModelInfo> listModelsWithApiKey(String apiKey, String apiEndpoint) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Flux.error(new RuntimeException("API密钥不能为空"));
        }

        try {
            // 获取API端点
            String baseUrl = apiEndpoint != null && !apiEndpoint.trim().isEmpty() ?
                    apiEndpoint : DEFAULT_API_ENDPOINT;

            // 调用X.AI API获取模型列表
            return this.webClient.get()
                    .uri("/models")
                    .header("Authorization", "Bearer " + apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .flatMapMany(response -> {
                        try {
                            // 解析响应
                            log.debug("X.AI模型列表响应: {}", response);
                            
                            // 使用ObjectMapper解析JSON响应
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode root = mapper.readTree(response);
                            JsonNode data = root.path("data");
                            
                            List<ModelInfo> models = new ArrayList<>();
                            
                            if (data.isArray()) {
                                for (JsonNode modelNode : data) {
                                    // 提取模型信息
                                    String id = modelNode.path("id").asText("");
                                    String object = modelNode.path("object").asText("model");
                                    long created = modelNode.path("created").asLong(0);
                                    String ownedBy = modelNode.path("owned_by").asText("xai");
                                    
                                    // 构建ModelInfo对象
                                    // 确保模型ID包含提供商前缀
                                    String fullModelId = id.startsWith("x-ai/") ? id : "x-ai/" + id;
                                    
                                    // 从TOKEN_PRICES获取价格信息
                                    double inputPrice = TOKEN_PRICES.getOrDefault(fullModelId + "-input", 0.003);
                                    double outputPrice = TOKEN_PRICES.getOrDefault(fullModelId + "-output", 0.006);
                                    
                                    // 创建模型信息
                                    ModelInfo modelInfo = ModelInfo.basic(fullModelId, fullModelId, "x-ai")
                                            .withDescription("X.AI的" + id + "模型，由" + ownedBy + "提供")
                                            .withMaxTokens(128000)  // 默认token上限
                                            .withInputPrice(inputPrice)
                                            .withOutputPrice(outputPrice);
                                    
                                    models.add(modelInfo);
                                    log.debug("解析到X.AI模型: {}, 输入价格: {}, 输出价格: {}", fullModelId, inputPrice, outputPrice);
                                }
                            }
                            
                            if (models.isEmpty()) {
                                log.warn("X.AI API返回的模型列表为空，将使用默认模型列表");
                                return Flux.fromIterable(getDefaultXAIModels());
                            }
                            
                            log.info("成功从X.AI API获取{}个模型", models.size());
                            return Flux.fromIterable(models);
                        } catch (Exception e) {
                            log.error("解析X.AI模型列表时出错: {}", e.getMessage(), e);
                            log.warn("由于解析错误，将返回默认模型列表");
                            return Flux.fromIterable(getDefaultXAIModels());
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("获取X.AI模型列表时出错: {}", e.getMessage(), e);
                        log.warn("由于API调用错误，将返回默认模型列表");
                        return Flux.fromIterable(getDefaultXAIModels());
                    });
        } catch (Exception e) {
            log.error("调用X.AI API时出错: {}", e.getMessage(), e);
            return Flux.fromIterable(getDefaultXAIModels());
        }
    }


    /**
     * 获取默认的X.AI模型列表
     * 
     * @return 模型信息列表
     */
    private List<ModelInfo> getDefaultXAIModels() {
        List<ModelInfo> models = new ArrayList<>();
        
        // 添加Grok-3-beta模型
        models.add(ModelInfo.basic("x-ai/grok-3-beta", "Grok-3-beta", "x-ai")
                .withDescription("X.AI的Grok-3-beta模型，支持强大的自然语言理解和生成能力")
                .withMaxTokens(128000)
                .withInputPrice(TOKEN_PRICES.getOrDefault("x-ai/grok-3-beta-input", 0.003))
                .withOutputPrice(TOKEN_PRICES.getOrDefault("x-ai/grok-3-beta-output", 0.006)));
        
        // 添加Grok-3模型
        models.add(ModelInfo.basic("x-ai/grok-3", "Grok-3", "x-ai")
                .withDescription("X.AI的Grok-3模型，最新版本，拥有强大的语言理解和生成能力")
                .withMaxTokens(128000)
                .withInputPrice(TOKEN_PRICES.getOrDefault("x-ai/grok-3-input", 0.003))
                .withOutputPrice(TOKEN_PRICES.getOrDefault("x-ai/grok-3-output", 0.006)));
        
        // 添加Grok-3-fast模型
        models.add(ModelInfo.basic("x-ai/grok-3-fast", "Grok-3-fast", "x-ai")
                .withDescription("X.AI的Grok-3-fast模型，更快的响应速度，适合对话场景")
                .withMaxTokens(128000)
                .withInputPrice(TOKEN_PRICES.getOrDefault("x-ai/grok-3-fast-input", 0.0015))
                .withOutputPrice(TOKEN_PRICES.getOrDefault("x-ai/grok-3-fast-output", 0.003)));
        
        // 添加Grok-3-mini模型
        models.add(ModelInfo.basic("x-ai/grok-3-mini", "Grok-3-mini", "x-ai")
                .withDescription("X.AI的Grok-3-mini模型，更小规模的模型，平衡性能和成本")
                .withMaxTokens(128000)
                .withInputPrice(TOKEN_PRICES.getOrDefault("x-ai/grok-3-mini-input", 0.0006))
                .withOutputPrice(TOKEN_PRICES.getOrDefault("x-ai/grok-3-mini-output", 0.0012)));
        
        // 添加Grok-3-mini-fast模型
        models.add(ModelInfo.basic("x-ai/grok-3-mini-fast", "Grok-3-mini-fast", "x-ai")
                .withDescription("X.AI的Grok-3-mini-fast模型，最经济实惠的选择，适合简单任务")
                .withMaxTokens(128000)
                .withInputPrice(TOKEN_PRICES.getOrDefault("x-ai/grok-3-mini-fast-input", 0.0003))
                .withOutputPrice(TOKEN_PRICES.getOrDefault("x-ai/grok-3-mini-fast-output", 0.0006)));
        
        // 添加Grok-2-vision模型
        models.add(ModelInfo.basic("x-ai/grok-2-vision-1212", "Grok-2-vision", "x-ai")
                .withDescription("X.AI的Grok-2-vision模型，支持图像理解能力")
                .withMaxTokens(128000)
                .withInputPrice(TOKEN_PRICES.getOrDefault("x-ai/grok-2-vision-1212-input", 0.003))
                .withOutputPrice(TOKEN_PRICES.getOrDefault("x-ai/grok-2-vision-1212-output", 0.006)));
        
        return models;
    }
    
    /**
     * 创建请求体
     * @param request AI请求
     * @param isStream 是否为流式请求
     * @return 请求体
     */
    private Map<String, Object> createRequestBody(AIRequest request, boolean isStream) {
        Map<String, Object> requestBody = new HashMap<>();
        
        // 处理模型名称，确保正确的格式
        String apiModel = modelName;
        
        // 如果模型名称以"x-ai/"开头，去掉前缀
        if (apiModel.startsWith("x-ai/")) {
            apiModel = apiModel.substring(5); // 去掉"x-ai/"前缀
        }
        
        requestBody.put("model", apiModel);
        requestBody.put("messages", convertMessages(request));
        
        // 设置温度
        if (request.getTemperature() != null) {
            requestBody.put("temperature", request.getTemperature());
        }
        
        // 设置最大令牌数
        if (request.getMaxTokens() != null) {
            requestBody.put("max_tokens", request.getMaxTokens());
        }
        
        // 如果是流式请求，设置stream参数
        if (isStream) {
            requestBody.put("stream", true);
        }
        
        return requestBody;
    }
    
    /**
     * 转换消息格式
     * @param request AI请求
     * @return 转换后的消息列表
     */
    private List<Map<String, Object>> convertMessages(AIRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        
        // 如果存在系统提示，添加为系统消息
        if (request.getPrompt() != null && !request.getPrompt().isEmpty()) {
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", request.getPrompt());
            messages.add(systemMessage);
        }
        
        // 添加消息历史
        if (request.getMessages() != null) {
            for (AIRequest.Message message : request.getMessages()) {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("role", message.getRole());
                messageMap.put("content", message.getContent());
                messages.add(messageMap);
            }
        }
        
        return messages;
    }
    
    /**
     * 将Grok响应转换为AIResponse
     * @param grokResponse Grok响应
     * @param request AI请求
     * @return AI响应
     */
    private AIResponse convertToAIResponse(GrokResponse grokResponse, AIRequest request) {
        AIResponse aiResponse = createBaseResponse("", request);
        
        if (grokResponse.getChoices() != null && !grokResponse.getChoices().isEmpty()) {
            GrokResponse.Choice choice = grokResponse.getChoices().get(0);
            if (choice.getMessage() != null) {
                aiResponse.setContent(choice.getMessage().getContent());
            }
            aiResponse.setFinishReason(choice.getFinishReason());
        }
        
        // 设置令牌使用情况
        if (grokResponse.getUsage() != null) {
            AIResponse.TokenUsage tokenUsage = new AIResponse.TokenUsage();
            tokenUsage.setPromptTokens(grokResponse.getUsage().getPromptTokens());
            tokenUsage.setCompletionTokens(grokResponse.getUsage().getCompletionTokens());
            
            // 设置总令牌数 - 可能AIResponse.TokenUsage没有直接的setter
            // 查看TokenUsage类的实现，总令牌数可能是自动计算的或需要通过其他方式设置
            try {
                // 尝试通过反射设置总令牌数，如果直接的setter不可用
                tokenUsage.getClass().getMethod("setTotalTokens", int.class)
                    .invoke(tokenUsage, grokResponse.getUsage().getTotalTokens());
            } catch (Exception e) {
                log.debug("无法直接设置总令牌数，可能会自动计算: {}", e.getMessage());
                // 总令牌数可能是输入+输出的总和，由TokenUsage类自动计算
            }
            
            aiResponse.setTokenUsage(tokenUsage);
        }
        
        return aiResponse;
    }
    
    /**
     * 估算输入令牌数
     * @param request AI请求
     * @return 估算的令牌数
     */
    private int estimateInputTokens(AIRequest request) {
        int tokenCount = 0;
        
        // 估算提示中的令牌数
        if (request.getPrompt() != null) {
            tokenCount += estimateTokenCount(request.getPrompt());
        }
        
        // 估算消息中的令牌数
        if (request.getMessages() != null) {
            for (AIRequest.Message message : request.getMessages()) {
                tokenCount += estimateTokenCount(message.getContent());
            }
        }
        
        return tokenCount;
    }
    
    /**
     * 估算文本的令牌数
     * @param text 文本
     * @return 令牌数
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 简单估算：对于中文，每个字约1.5个令牌；对于英文，每个单词约1.3个令牌
        // 这里使用简单的估算方法，实际上应该使用更准确的分词算法
        return (int) (text.length() * 0.75);
    }
    
    /**
     * Grok API响应结构
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GrokResponse {
        private String id;
        private String object;
        private long created;
        private String model;
        private List<Choice> choices;
        private Usage usage;
        @JsonProperty("system_fingerprint")
        private String systemFingerprint;
        
        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Choice {
            private int index;
            private Message message;
            private Delta delta;
            @JsonProperty("finish_reason")
            private String finishReason;
            
            @Override
            public String toString() {
                return "Choice(index=" + index + 
                       ", message=" + message + 
                       ", delta=" + delta + 
                       ", finishReason=" + finishReason + ")";
            }
        }
        
        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Message {
            private String role;
            private String content;
            
            @Override
            public String toString() {
                return "Message(role=" + role + ", content=" + content + ")";
            }
        }
        
        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Delta {
            private String role;
            private String content;
            
            @Override
            public String toString() {
                return "Delta(role=" + role + ", content=" + content + ")";
            }
        }
        
        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Usage {
            @JsonProperty("prompt_tokens")
            private int promptTokens;
            @JsonProperty("completion_tokens")
            private int completionTokens;
            @JsonProperty("total_tokens")
            private int totalTokens;
        }
        
        @Override
        public String toString() {
            return "GrokResponse(id=" + id + 
                   ", object=" + object + 
                   ", created=" + created + 
                   ", model=" + model + 
                   ", choices=" + (choices != null ? choices.size() : "null") + 
                   ", usage=" + usage + 
                   ", systemFingerprint=" + systemFingerprint + ")";
        }
    }
} 