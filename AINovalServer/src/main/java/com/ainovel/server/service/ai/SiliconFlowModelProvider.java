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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * 硅基流动大模型平台提供商
 */
@Slf4j
public class SiliconFlowModelProvider extends AbstractAIModelProvider {

    private static final String DEFAULT_API_ENDPOINT = "https://api.siliconflow.cn/v1";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private WebClient webClient;
    private final String apiUrl;
    
    /**
     * 构造函数
     * @param modelName 模型名称
     * @param apiKey API密钥
     * @param apiEndpoint API端点
     */
    public SiliconFlowModelProvider(String modelName, String apiKey, String apiEndpoint) {
        super("siliconflow", modelName, apiKey, apiEndpoint);
        this.apiUrl = getApiEndpoint(DEFAULT_API_ENDPOINT);
        initWebClient();
    }
    
    /**
     * 初始化WebClient
     */
    private void initWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30)) // 设置响应超时
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
            requestBody.put("model", modelName);
            requestBody.put("messages", convertMessages(request));
            requestBody.put("temperature", request.getTemperature());
            requestBody.put("max_tokens", request.getMaxTokens());
            
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
                            SiliconFlowResponse siliconFlowResponse = objectMapper.readValue(responseJson, SiliconFlowResponse.class);
                            return convertToAIResponse(siliconFlowResponse, request);
                        } catch (Exception e) {
                            log.error("解析SiliconFlow响应失败", e);
                            AIResponse errorResponse = createBaseResponse("解析响应失败: " + e.getMessage(), request);
                            errorResponse.setFinishReason("error");
                            return errorResponse;
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("SiliconFlow API调用失败", e);
                        return handleApiException(e, request);
                    });
        } catch (Exception e) {
            log.error("SiliconFlow API调用失败", e);
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
            requestBody.put("model", modelName);
            requestBody.put("messages", convertMessages(request));
            requestBody.put("temperature", request.getTemperature());
            requestBody.put("max_tokens", request.getMaxTokens());
            requestBody.put("stream", true); // 启用流式输出
            
            // 调用流式API
            return webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
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
                            
                            SiliconFlowStreamResponse streamResponse = objectMapper.readValue(chunk, SiliconFlowStreamResponse.class);
                            if (streamResponse.getChoices() != null && !streamResponse.getChoices().isEmpty()) {
                                SiliconFlowStreamResponse.Choice choice = streamResponse.getChoices().get(0);
                                if (choice.getDelta() != null && choice.getDelta().getContent() != null) {
                                    return choice.getDelta().getContent();
                                }
                            }
                            return "";
                        } catch (Exception e) {
                            log.error("解析SiliconFlow流式响应失败", e);
                            return "错误：" + e.getMessage();
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("SiliconFlow流式API调用失败", e);
                        return Flux.just("错误：" + e.getMessage());
                    });
        } catch (Exception e) {
            log.error("SiliconFlow流式API调用失败", e);
            return Flux.just("错误：" + e.getMessage());
        }
    }

    @Override
    public Mono<Double> estimateCost(AIRequest request) {
        // 硅基流动平台的价格估算（根据实际价格调整）
        // 这里使用一个估算值，实际应根据硅基流动平台的价格政策调整
        double inputPricePerToken = 0.0001 / 1000; // 输入价格：假设为$0.0001/1K tokens
        double outputPricePerToken = 0.0002 / 1000; // 输出价格：假设为$0.0002/1K tokens
        
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
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", "Hello");
        messages.add(message);
        
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 5);
        
        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> true)
                .onErrorReturn(false)
                .doOnError(e -> log.error("验证SiliconFlow API密钥失败", e));
    }
    
    /**
     * 测试SiliconFlow API连接
     * 使用写死的请求参数和内容，方便快速测试API是否正常工作
     * @return 测试结果
     */
    public Mono<String> testSiliconFlowApi() {
        if (isApiKeyEmpty()) {
            return Mono.just("错误：API密钥未配置");
        }
        
        try {
            // 构建简单的请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", "你好，请用中文介绍一下自己，你是什么模型？");
            messages.add(message);
            
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 1000);
            
            // 调用API
            log.info("开始测试SiliconFlow API，模型：{}，请求体：{}", modelName, requestBody);
            
            return webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(responseJson -> {
                        log.info("SiliconFlow API测试响应：{}", responseJson);
                        try {
                            SiliconFlowResponse siliconFlowResponse = objectMapper.readValue(responseJson, SiliconFlowResponse.class);
                            if (siliconFlowResponse.getChoices() != null && !siliconFlowResponse.getChoices().isEmpty()) {
                                SiliconFlowResponse.Choice choice = siliconFlowResponse.getChoices().get(0);
                                if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                                    return "测试成功，响应内容：" + choice.getMessage().getContent();
                                }
                            }
                            return "测试成功，但无法解析响应内容";
                        } catch (Exception e) {
                            log.error("解析SiliconFlow测试响应失败", e);
                            return "测试失败，解析响应出错：" + e.getMessage() + "\n原始响应：" + responseJson;
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("SiliconFlow API测试调用失败", e);
                        return Mono.just("测试失败，API调用出错：" + e.getMessage());
                    });
        } catch (Exception e) {
            log.error("SiliconFlow API测试准备失败", e);
            return Mono.just("测试失败，准备请求出错：" + e.getMessage());
        }
    }
    
    /**
     * 转换消息格式
     * @param request AI请求
     * @return SiliconFlow格式的消息列表
     */
    private List<Map<String, Object>> convertMessages(AIRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        
        // 如果有提示内容，添加为系统消息
        if (request.getPrompt() != null && !request.getPrompt().isEmpty()) {
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", request.getPrompt());
            messages.add(systemMessage);
        }
        
        // 添加对话历史
        for (Message message : request.getMessages()) {
            Map<String, Object> siliconFlowMessage = new HashMap<>();
            siliconFlowMessage.put("content", message.getContent());
            
            switch (message.getRole().toLowerCase()) {
                case "user":
                    siliconFlowMessage.put("role", "user");
                    break;
                case "assistant":
                    siliconFlowMessage.put("role", "assistant");
                    break;
                case "system":
                    siliconFlowMessage.put("role", "system");
                    break;
                default:
                    log.warn("未知的消息角色: {}", message.getRole());
                    siliconFlowMessage.put("role", "user");
            }
            
            messages.add(siliconFlowMessage);
        }
        
        return messages;
    }
    
    /**
     * 将SiliconFlow响应转换为AI响应
     * @param siliconFlowResponse SiliconFlow响应
     * @param request 原始请求
     * @return AI响应
     */
    private AIResponse convertToAIResponse(SiliconFlowResponse siliconFlowResponse, AIRequest request) {
        AIResponse aiResponse = createBaseResponse("", request);
        
        if (siliconFlowResponse.getChoices() != null && !siliconFlowResponse.getChoices().isEmpty()) {
            SiliconFlowResponse.Choice choice = siliconFlowResponse.getChoices().get(0);
            
            if (choice.getMessage() != null) {
                // 设置内容
                if (choice.getMessage().getContent() != null) {
                    aiResponse.setContent(choice.getMessage().getContent());
                }
                
                // 设置推理内容（如果有）
                if (choice.getMessage().getReasoningContent() != null) {
                    aiResponse.setReasoningContent(choice.getMessage().getReasoningContent());
                }
                
                // 处理工具调用（如果有）
                if (choice.getMessage().getToolCalls() != null && !choice.getMessage().getToolCalls().isEmpty()) {
                    List<AIResponse.ToolCall> toolCalls = new ArrayList<>();
                    
                    for (SiliconFlowResponse.ToolCall toolCall : choice.getMessage().getToolCalls()) {
                        AIResponse.ToolCall aiToolCall = new AIResponse.ToolCall();
                        aiToolCall.setId(toolCall.getId());
                        aiToolCall.setType(toolCall.getType());
                        
                        if (toolCall.getFunction() != null) {
                            AIResponse.Function function = new AIResponse.Function();
                            function.setName(toolCall.getFunction().getName());
                            function.setArguments(toolCall.getFunction().getArguments());
                            aiToolCall.setFunction(function);
                        }
                        
                        toolCalls.add(aiToolCall);
                    }
                    
                    aiResponse.setToolCalls(toolCalls);
                }
            }
            
            // 设置完成原因
            aiResponse.setFinishReason(choice.getFinishReason());
        } else {
            aiResponse.setFinishReason("error");
            aiResponse.setContent("无有效响应");
        }
        
        // 设置令牌使用情况
        if (siliconFlowResponse.getUsage() != null) {
            AIResponse.TokenUsage usage = new AIResponse.TokenUsage();
            usage.setPromptTokens(siliconFlowResponse.getUsage().getPromptTokens());
            usage.setCompletionTokens(siliconFlowResponse.getUsage().getCompletionTokens());
            aiResponse.setTokenUsage(usage);
        }
        
        return aiResponse;
    }
    
    /**
     * SiliconFlow API响应模型
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SiliconFlowResponse {
        private String id;
        private List<Choice> choices;
        private Usage usage;
        private long created;
        private String model;
        private String object;
        @JsonProperty("system_fingerprint")
        private String systemFingerprint;
        
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Choice {
            private Message message;
            @JsonProperty("finish_reason")
            private String finishReason;
            private int index;
        }
        
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Message {
            private String role;
            private String content;
            @JsonProperty("reasoning_content")
            private String reasoningContent;
            @JsonProperty("tool_calls")
            private List<ToolCall> toolCalls;
        }
        
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ToolCall {
            private String id;
            private String type;
            private Function function;
        }
        
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Function {
            private String name;
            private String arguments;
        }
        
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Usage {
            @JsonProperty("prompt_tokens")
            private int promptTokens;
            @JsonProperty("completion_tokens")
            private int completionTokens;
            @JsonProperty("total_tokens")
            private int totalTokens;
        }
    }
    
    /**
     * SiliconFlow 流式响应模型
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SiliconFlowStreamResponse {
        private String id;
        private List<Choice> choices;
        private long created;
        private String model;
        private String object;
        @JsonProperty("system_fingerprint")
        private String systemFingerprint;
        
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Choice {
            private Delta delta;
            @JsonProperty("finish_reason")
            private String finishReason;
            private int index;
        }
        
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Delta {
            private String role;
            private String content;
        }
    }
} 