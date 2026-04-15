package com.ainovel.server.service.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.ainovel.server.domain.model.AIRequest;
import com.ainovel.server.domain.model.AIResponse;
import com.ainovel.server.domain.model.AIResponse.TokenUsage;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;
import reactor.util.retry.Retry;

/**
 * Anthropic模型提供商实现
 */
public class AnthropicModelProvider extends AbstractAIModelProvider {
    
    private static final String DEFAULT_API_ENDPOINT = "https://api.anthropic.com/v1";
    private static final Map<String, Double> TOKEN_PRICES = Map.of(
            "claude-3-opus", 0.015,
            "claude-3-sonnet", 0.003,
            "claude-3-haiku", 0.00025,
            "claude-2", 0.008
    );
    
    private WebClient webClient;
    
    /**
     * 构造函数
     * @param modelName 模型名称
     * @param apiKey API密钥
     * @param apiEndpoint API端点
     */
    public AnthropicModelProvider(String modelName, String apiKey, String apiEndpoint) {
        super("anthropic", modelName, apiKey, apiEndpoint);
        initWebClient();
    }
    
    /**
     * 初始化WebClient
     */
    private void initWebClient() {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(getApiEndpoint(DEFAULT_API_ENDPOINT))
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json");
        
        if (proxyEnabled) {
            try {
                HttpClient httpClient = HttpClient.create()
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
                }
                
                builder.clientConnector(new ReactorClientHttpConnector(httpClient));
            } catch (Exception e) {
                System.err.println("配置代理时出错: " + e.getMessage());
            }
        }
        
        this.webClient = builder.build();
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
    public String getProviderName() {
        return providerName;
    }
    
    @Override
    public String getModelName() {
        return modelName;
    }
    
    @Override
    public Mono<AIResponse> generateContent(AIRequest request) {
        if (isApiKeyEmpty()) {
            return Mono.just(createBaseResponse("API密钥未配置", request));
        }
        
        Map<String, Object> requestBody = createRequestBody(request, false);
        
        return webClient.post()
                .uri("/messages")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    String content = extractContentFromResponse(response);
                    AIResponse aiResponse = createBaseResponse(content, request);
                    
                    // 设置令牌使用情况
                    Map<String, Object> usage = (Map<String, Object>) response.get("usage");
                    if (usage != null) {
                        TokenUsage tokenUsage = new TokenUsage();
                        tokenUsage.setPromptTokens(((Number) usage.get("input_tokens")).intValue());
                        tokenUsage.setCompletionTokens(((Number) usage.get("output_tokens")).intValue());
                        aiResponse.setTokenUsage(tokenUsage);
                    }
                    
                    // 设置完成原因
                    aiResponse.setFinishReason((String) response.get("stop_reason"));
                    
                    return aiResponse;
                })
                .onErrorResume(e -> handleApiException(e, request))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(e -> !(e instanceof IllegalArgumentException)));
    }
    
    @Override
    public Flux<String> generateContentStream(AIRequest request) {
        if (isApiKeyEmpty()) {
            return Flux.just("API密钥未配置");
        }
        
        Map<String, Object> requestBody = createRequestBody(request, true);
        
        return webClient.post()
                .uri("/messages")
                .bodyValue(requestBody)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(Map.class)
                .map(this::extractContentFromStreamResponse)
                .onErrorResume(e -> Flux.just("API调用失败: " + e.getMessage()))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(e -> !(e instanceof IllegalArgumentException)));
    }
    
    @Override
    public Mono<Double> estimateCost(AIRequest request) {
        // 简单估算，基于输入令牌数
        AtomicInteger tokenCount = new AtomicInteger(0);
        
        // 估算提示中的令牌数
        if (request.getPrompt() != null) {
            tokenCount.addAndGet(estimateTokenCount(request.getPrompt()));
        }
        
        // 估算消息中的令牌数
        request.getMessages().forEach(message -> 
            tokenCount.addAndGet(estimateTokenCount(message.getContent()))
        );
        
        // 估算最大输出令牌数
        int outputTokens = request.getMaxTokens() != null ? request.getMaxTokens() : 1000;
        
        // 计算总令牌数
        int totalTokens = tokenCount.get() + outputTokens;
        
        // 获取模型价格（每1000个令牌的美元价格）
        double pricePerThousandTokens = TOKEN_PRICES.getOrDefault(modelName, 0.01);
        
        // 计算成本（美元）
        double costInUSD = (totalTokens / 1000.0) * pricePerThousandTokens;
        
        // 转换为人民币（假设汇率为7）
        double costInCNY = costInUSD * 7;
        
        return Mono.just(costInCNY);
    }
    
    @Override
    public Mono<Boolean> validateApiKey() {
        if (isApiKeyEmpty()) {
            return Mono.just(false);
        }
        
        // Anthropic没有专门的验证端点，尝试获取模型列表
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("max_tokens", 1);
        requestBody.put("messages", List.of(Map.of("role", "user", "content", "Hello")));
        
        return webClient.post()
                .uri("/messages")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> true)
                .onErrorReturn(false);
    }
    
    /**
     * 创建请求体
     * @param request AI请求
     * @param stream 是否流式请求
     * @return 请求体
     */
    private Map<String, Object> createRequestBody(AIRequest request, boolean stream) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("stream", stream);
        
        // 设置温度
        if (request.getTemperature() != null) {
            requestBody.put("temperature", request.getTemperature());
        }
        
        // 设置最大令牌数
        if (request.getMaxTokens() != null) {
            requestBody.put("max_tokens", request.getMaxTokens());
        }
        
        // 设置系统提示
        if (request.getPrompt() != null && !request.getPrompt().isEmpty()) {
            requestBody.put("system", request.getPrompt());
        }
        
        // 设置消息
        List<Map<String, String>> messages = new ArrayList<>();
        
        // 添加用户消息
        request.getMessages().forEach(message -> {
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put("role", convertRole(message.getRole()));
            messageMap.put("content", message.getContent());
            messages.add(messageMap);
        });
        
        requestBody.put("messages", messages);
        
        return requestBody;
    }
    
    /**
     * 转换角色名称（OpenAI格式转Anthropic格式）
     * @param role OpenAI角色名称
     * @return Anthropic角色名称
     */
    private String convertRole(String role) {
        return switch (role) {
            case "assistant" -> "assistant";
            default -> "user";
        };
    }
    
    /**
     * 从响应中提取内容
     * @param response 响应
     * @return 内容
     */
    private String extractContentFromResponse(Map<String, Object> response) {
        Map<String, Object> content = (Map<String, Object>) ((List<Map<String, Object>>) response.get("content")).get(0);
        if (content != null && content.get("type").equals("text")) {
            return (String) content.get("text");
        }
        return "";
    }
    
    /**
     * 从流式响应中提取内容
     * @param response 响应
     * @return 内容
     */
    private String extractContentFromStreamResponse(Map<String, Object> response) {
        if (response.containsKey("delta") && ((Map<String, Object>) response.get("delta")).containsKey("text")) {
            return (String) ((Map<String, Object>) response.get("delta")).get("text");
        }
        return "";
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
        // 简单估算：平均每个单词1.3个令牌
        return (int) (text.split("\\s+").length * 1.3);
    }
} 