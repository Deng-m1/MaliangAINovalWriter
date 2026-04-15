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
 * OpenAI模型提供商实现
 */
public class OpenAIModelProvider extends AbstractAIModelProvider {
    
    private static final String DEFAULT_API_ENDPOINT = "https://api.openai.com/v1";
    private static final Map<String, Double> TOKEN_PRICES = Map.of(
            "gpt-3.5-turbo", 0.0015,
            "gpt-3.5-turbo-16k", 0.003,
            "gpt-4", 0.03,
            "gpt-4-32k", 0.06,
            "gpt-4-turbo", 0.01,
            "gpt-4o", 0.01
    );
    
    private WebClient webClient;
    
    /**
     * 构造函数
     * @param modelName 模型名称
     * @param apiKey API密钥
     * @param apiEndpoint API端点
     */
    public OpenAIModelProvider(String modelName, String apiKey, String apiEndpoint) {
        super("openai", modelName, apiKey, apiEndpoint);
        initWebClient();
    }
    
    /**
     * 初始化WebClient
     */
    private void initWebClient() {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(getApiEndpoint(DEFAULT_API_ENDPOINT))
                .defaultHeader("Authorization", "Bearer " + apiKey)
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
    public Mono<AIResponse> generateContent(AIRequest request) {
        if (isApiKeyEmpty()) {
            return Mono.just(createBaseResponse("API密钥未配置", request));
        }
        
        Map<String, Object> requestBody = createRequestBody(request, false);
        
        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    String content = extractContentFromResponse(response);
                    AIResponse aiResponse = createBaseResponse(content, request);
                    
                    // 设置令牌使用情况
                    Map<String, Object> usage = (Map<String, Object>) response.get("usage");
                    if (usage != null) {
                        TokenUsage tokenUsage = new TokenUsage(
                                ((Number) usage.get("prompt_tokens")).intValue(),
                                ((Number) usage.get("completion_tokens")).intValue()
                        );
                        aiResponse.setTokenUsage(tokenUsage);
                    }
                    
                    // 设置完成原因
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        aiResponse.setFinishReason((String) choices.get(0).get("finish_reason"));
                    }
                    
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
                .uri("/chat/completions")
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
        
        return webClient.get()
                .uri("/models")
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
        
        // 设置消息
        List<Map<String, String>> messages = new ArrayList<>();
        
        // 如果有提示，添加系统消息
        if (request.getPrompt() != null && !request.getPrompt().isEmpty()) {
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", request.getPrompt());
            messages.add(systemMessage);
        }
        
        // 添加用户消息
        request.getMessages().forEach(message -> {
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put("role", message.getRole());
            messageMap.put("content", message.getContent());
            messages.add(messageMap);
        });
        
        requestBody.put("messages", messages);
        
        return requestBody;
    }
    
    /**
     * 从响应中提取内容
     * @param response 响应
     * @return 内容
     */
    private String extractContentFromResponse(Map<String, Object> response) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message != null) {
                return (String) message.get("content");
            }
        }
        return "";
    }
    
    /**
     * 从流式响应中提取内容
     * @param response 响应
     * @return 内容
     */
    private String extractContentFromStreamResponse(Map<String, Object> response) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
            if (delta != null && delta.containsKey("content")) {
                return (String) delta.get("content");
            }
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