package com.ainovel.server.service.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.ainovel.server.domain.model.AIRequest;
import com.ainovel.server.domain.model.AIRequest.Message;
import com.ainovel.server.domain.model.AIResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

/**
 * 谷歌Gemini模型提供商
 */
@Slf4j
public class GeminiModelProvider extends AbstractAIModelProvider {

    private static final String DEFAULT_API_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private WebClient webClient;
    private final String apiUrl;
    
    /**
     * 构造函数
     * @param modelName 模型名称
     * @param apiKey API密钥
     * @param apiEndpoint API端点
     */
    public GeminiModelProvider(String modelName, String apiKey, String apiEndpoint) {
        super("gemini", modelName, apiKey, apiEndpoint);
        this.apiUrl = getApiEndpoint(DEFAULT_API_ENDPOINT);
        initWebClient();
    }
    
    /**
     * 初始化WebClient
     */
    private void initWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(5)) // 设置响应超时
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000); // 设置连接超时
        
        if (proxyEnabled) {
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
                    log.warn("已启用 trustAllCerts（仅建议用于排障），生产请关闭！");
                }
                
                log.info("已启用代理: {}:{}", proxyHost, proxyPort);
            } catch (Exception e) {
                log.error("配置代理时出错: {}", e.getMessage(), e);
            }
        }
        
        this.webClient = WebClient.builder()
                .baseUrl(this.apiUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
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
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", convertMessages(request));
            
            // 设置生成参数
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", request.getTemperature());
            generationConfig.put("maxOutputTokens", request.getMaxTokens());
            requestBody.put("generationConfig", generationConfig);
            
            // 调用API
            return webClient.post()
                    .uri("/{model}:generateContent?key={apiKey}", modelName, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(responseJson -> {
                        try {
                            GeminiResponse geminiResponse = objectMapper.readValue(responseJson, GeminiResponse.class);
                            return convertToAIResponse(geminiResponse, request);
                        } catch (Exception e) {
                            log.error("解析Gemini响应失败", e);
                            AIResponse errorResponse = createBaseResponse("解析响应失败: " + e.getMessage(), request);
                            errorResponse.setFinishReason("error");
                            return errorResponse;
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("Gemini API调用失败", e);
                        return handleApiException(e, request);
                    });
        } catch (Exception e) {
            log.error("Gemini API调用失败", e);
            return handleApiException(e, request);
        }
    }

    @Override
    public Flux<String> generateContentStream(AIRequest request) {
        if (isApiKeyEmpty()) {
            return Flux.just("错误：API密钥未配置");
        }

        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", convertMessages(request));
            
            // 设置生成参数
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", request.getTemperature());
            generationConfig.put("maxOutputTokens", request.getMaxTokens());
            requestBody.put("generationConfig", generationConfig);
            
            // 调用流式API
            return webClient.post()
                    .uri("/{model}:streamGenerateContent?key={apiKey}", modelName, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .map(chunk -> {
                        try {
                            // 解析流式响应
                            if (chunk.startsWith("data: ")) {
                                chunk = chunk.substring(6);
                            }
                            if (chunk.equals("[DONE]")) {
                                return "";
                            }
                            
                            GeminiResponse geminiResponse = objectMapper.readValue(chunk, GeminiResponse.class);
                            if (geminiResponse.getCandidates() != null && !geminiResponse.getCandidates().isEmpty()) {
                                GeminiResponse.GeminiCandidate candidate = geminiResponse.getCandidates().get(0);
                                if (candidate.getContent() != null && candidate.getContent().getParts() != null && 
                                    !candidate.getContent().getParts().isEmpty()) {
                                    return candidate.getContent().getParts().get(0).getText();
                                }
                            }
                            return "";
                        } catch (Exception e) {
                            log.error("解析Gemini流式响应失败", e);
                            return "错误：" + e.getMessage();
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("Gemini流式API调用失败", e);
                        return Flux.just("错误：" + e.getMessage());
                    });
        } catch (Exception e) {
            log.error("Gemini流式API调用失败", e);
            return Flux.just("错误：" + e.getMessage());
        }
    }

    @Override
    public Mono<Double> estimateCost(AIRequest request) {
        // Gemini API的价格估算（根据实际价格调整）
        // 参考：https://ai.google.dev/pricing
        double inputPricePerToken = 0.000125 / 1000; // 输入价格：$0.000125/1K tokens
        double outputPricePerToken = 0.000375 / 1000; // 输出价格：$0.000375/1K tokens
        
        // 估算输入令牌数（简单估算，实际应使用分词器）
        int estimatedInputTokens = 0;
        if (request.getPrompt() != null) {
            estimatedInputTokens += request.getPrompt().length() / 4;
        }
        
        for (Message message : request.getMessages()) {
            estimatedInputTokens += message.getContent().length() / 4;
        }
        
        // 估算输出令牌数
        int estimatedOutputTokens = request.getMaxTokens();
        
        // 计算总成本（美元）
        double costInUsd = (estimatedInputTokens * inputPricePerToken) + 
                          (estimatedOutputTokens * outputPricePerToken);
        
        // 转换为人民币（假设汇率为7.2）
        double costInCny = costInUsd * 7.2;
        
        return Mono.just(costInCny);
    }

    @Override
    public Mono<Boolean> validateApiKey() {
        if (isApiKeyEmpty()) {
            return Mono.just(false);
        }
        
        // 创建一个简单的请求来验证API密钥
        return webClient.post()
                .uri("/models?key={apiKey}", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"contents\": [{\"parts\":[{\"text\": \"Explain how AI works\"}]}]}")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> true)
                .onErrorReturn(false)
                .doOnError(e -> log.error("验证Gemini API密钥失败", e));
    }
    
    /**
     * 测试Gemini API连接
     * 使用写死的请求参数和内容，方便快速测试API是否正常工作
     * @return 测试结果
     */
    public Mono<String> testGeminiApi() {
        if (isApiKeyEmpty()) {
            return Mono.just("错误：API密钥未配置");
        }
        
        try {
            // 构建简单的请求体
            Map<String, Object> requestBody = new HashMap<>();
            List<Map<String, Object>> contents = new ArrayList<>();
            
            // 创建用户消息
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            
            List<Map<String, Object>> parts = new ArrayList<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", "你好，请用中文介绍一下自己，你是什么模型？");
            parts.add(part);
            
            userMessage.put("parts", parts);
            contents.add(userMessage);
            
            requestBody.put("contents", contents);
            
            // 设置生成参数
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("maxOutputTokens", 1000);
            requestBody.put("generationConfig", generationConfig);
            
            // 调用API
            log.info("开始测试Gemini API，模型：{}，请求体：{}", modelName, requestBody);
            
            return webClient.post()
                    .uri("/{model}:generateContent?key={apiKey}", modelName, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(responseJson -> {
                        log.info("Gemini API测试响应：{}", responseJson);
                        try {
                            GeminiResponse geminiResponse = objectMapper.readValue(responseJson, GeminiResponse.class);
                            if (geminiResponse.getCandidates() != null && !geminiResponse.getCandidates().isEmpty()) {
                                GeminiResponse.GeminiCandidate candidate = geminiResponse.getCandidates().get(0);
                                if (candidate.getContent() != null && candidate.getContent().getParts() != null && 
                                    !candidate.getContent().getParts().isEmpty()) {
                                    return "测试成功，响应内容：" + candidate.getContent().getParts().get(0).getText();
                                }
                            }
                            return "测试成功，但无法解析响应内容";
                        } catch (Exception e) {
                            log.error("解析Gemini测试响应失败", e);
                            return "测试失败，解析响应出错：" + e.getMessage() + "\n原始响应：" + responseJson;
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("Gemini API测试调用失败", e);
                        return Mono.just("测试失败，API调用出错：" + e.getMessage());
                    });
        } catch (Exception e) {
            log.error("Gemini API测试准备失败", e);
            return Mono.just("测试失败，准备请求出错：" + e.getMessage());
        }
    }
    
    /**
     * 转换消息格式
     * @param request AI请求
     * @return Gemini格式的消息列表
     */
    private List<Map<String, Object>> convertMessages(AIRequest request) {
        List<Map<String, Object>> contents = new ArrayList<>();
        
        // 如果有提示内容，添加为系统消息
        if (request.getPrompt() != null && !request.getPrompt().isEmpty()) {
            Map<String, Object> systemMessage = new HashMap<>();
            List<Map<String, Object>> parts = new ArrayList<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", request.getPrompt());
            parts.add(part);
            systemMessage.put("role", "user");
            systemMessage.put("parts", parts);
            contents.add(systemMessage);
            
            // 添加一个空的助手响应，以便后续消息正确处理
            Map<String, Object> emptyAssistantMessage = new HashMap<>();
            List<Map<String, Object>> emptyParts = new ArrayList<>();
            Map<String, Object> emptyPart = new HashMap<>();
            emptyPart.put("text", "我明白了。");
            emptyParts.add(emptyPart);
            emptyAssistantMessage.put("role", "model");
            emptyAssistantMessage.put("parts", emptyParts);
            contents.add(emptyAssistantMessage);
        }
        
        // 添加对话历史
        for (Message message : request.getMessages()) {
            Map<String, Object> geminiMessage = new HashMap<>();
            List<Map<String, Object>> parts = new ArrayList<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", message.getContent());
            parts.add(part);
            
            switch (message.getRole().toLowerCase()) {
                case "user":
                    geminiMessage.put("role", "user");
                    break;
                case "assistant":
                    geminiMessage.put("role", "model");
                    break;
                case "system":
                    // Gemini不直接支持系统消息，将其作为用户消息处理
                    geminiMessage.put("role", "user");
                    break;
                default:
                    log.warn("未知的消息角色: {}", message.getRole());
                    geminiMessage.put("role", "user");
            }
            
            geminiMessage.put("parts", parts);
            contents.add(geminiMessage);
        }
        
        return contents;
    }
    
    /**
     * 将Gemini响应转换为AI响应
     * @param geminiResponse Gemini响应
     * @param request 原始请求
     * @return AI响应
     */
    private AIResponse convertToAIResponse(GeminiResponse geminiResponse, AIRequest request) {
        AIResponse aiResponse = createBaseResponse("", request);
        
        if (geminiResponse.getCandidates() != null && !geminiResponse.getCandidates().isEmpty()) {
            GeminiResponse.GeminiCandidate candidate = geminiResponse.getCandidates().get(0);
            
            // 设置内容
            if (candidate.getContent() != null && candidate.getContent().getParts() != null && 
                !candidate.getContent().getParts().isEmpty()) {
                aiResponse.setContent(candidate.getContent().getParts().get(0).getText());
            }
            
            // 设置完成原因
            aiResponse.setFinishReason(candidate.getFinishReason());
        } else {
            aiResponse.setFinishReason("error");
            aiResponse.setContent("无有效响应");
        }
        
        // 设置令牌使用情况
        if (geminiResponse.getUsage() != null) {
            AIResponse.TokenUsage usage = new AIResponse.TokenUsage();
            usage.setPromptTokens(geminiResponse.getUsage().getPromptTokenCount());
            usage.setCompletionTokens(geminiResponse.getUsage().getCandidatesTokenCount());
            aiResponse.setTokenUsage(usage);
        }
        
        return aiResponse;
    }
    
    /**
     * Gemini API响应模型
     */
    @Data
    private static class GeminiResponse {
        private List<GeminiCandidate> candidates;
        private GeminiUsage usage;
        
        @Data
        public static class GeminiCandidate {
            private GeminiContent content;
            @JsonProperty("finishReason")
            private String finishReason;
            private int index;
        }
        
        @Data
        public static class GeminiContent {
            private List<GeminiPart> parts;
            private String role;
        }
        
        @Data
        public static class GeminiPart {
            private String text;
        }
        
        @Data
        public static class GeminiUsage {
            @JsonProperty("promptTokenCount")
            private int promptTokenCount;
            @JsonProperty("candidatesTokenCount")
            private int candidatesTokenCount;
            @JsonProperty("totalTokenCount")
            private int totalTokenCount;
        }
    }
} 