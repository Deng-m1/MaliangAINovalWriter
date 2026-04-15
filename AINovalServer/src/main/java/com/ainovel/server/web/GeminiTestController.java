package com.ainovel.server.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ainovel.server.domain.model.AIRequest;
import com.ainovel.server.domain.model.AIResponse;
import com.ainovel.server.service.ai.GeminiModelProvider;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Gemini API测试控制器
 * Restricted to dev and test profiles only.
 */
@RestController
@RequestMapping("/api/test/gemini")
@Slf4j
@org.springframework.context.annotation.Profile({"dev", "test", "open-dev"})
public class GeminiTestController {
    
    /**
     * 测试Gemini API（非流式）
     * @param request 测试请求
     * @return AI响应
     */
    @PostMapping
    public Mono<AIResponse> testGemini(@RequestBody GeminiTestRequest request) {
        log.info("收到Gemini测试请求: {}", request);
        
        // 创建AI请求
        AIRequest aiRequest = new AIRequest();
        aiRequest.setUserId("test-user");
        aiRequest.setModel(request.getModel());
        aiRequest.setPrompt(request.getPrompt());
        aiRequest.setTemperature(request.getTemperature());
        aiRequest.setMaxTokens(request.getMaxTokens());
        
        // 添加消息
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            List<AIRequest.Message> messages = new ArrayList<>();
            for (GeminiMessage message : request.getMessages()) {
                AIRequest.Message aiMessage = new AIRequest.Message();
                aiMessage.setRole(message.getRole());
                aiMessage.setContent(message.getContent());
                messages.add(aiMessage);
            }
            aiRequest.setMessages(messages);
        }
        
        // 创建Gemini模型提供商
        GeminiModelProvider provider = new GeminiModelProvider(
                request.getModel(), 
                request.getApiKey(),
                null);
        
        // 调用API
        return provider.generateContent(aiRequest);
    }
    
    /**
     * 测试Gemini API（流式）
     * @param request 测试请求
     * @return 流式响应
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> testGeminiStream(@RequestBody GeminiTestRequest request) {
        log.info("收到Gemini流式测试请求: {}", request);
        
        // 创建AI请求
        AIRequest aiRequest = new AIRequest();
        aiRequest.setUserId("test-user");
        aiRequest.setModel(request.getModel());
        aiRequest.setPrompt(request.getPrompt());
        aiRequest.setTemperature(request.getTemperature());
        aiRequest.setMaxTokens(request.getMaxTokens());
        
        // 添加消息
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            List<AIRequest.Message> messages = new ArrayList<>();
            for (GeminiMessage message : request.getMessages()) {
                AIRequest.Message aiMessage = new AIRequest.Message();
                aiMessage.setRole(message.getRole());
                aiMessage.setContent(message.getContent());
                messages.add(aiMessage);
            }
            aiRequest.setMessages(messages);
        }
        
        // 创建Gemini模型提供商
        GeminiModelProvider provider = new GeminiModelProvider(
                request.getModel(), 
                request.getApiKey(),
                null);
        
        // 调用流式API
        return provider.generateContentStream(aiRequest);
    }
    
    /**
     * 验证API密钥
     * @param request 测试请求
     * @return 验证结果
     */
    @PostMapping("/validate")
    public Mono<Map<String, Boolean>> validateApiKey(@RequestBody GeminiTestRequest request) {
        log.info("收到Gemini API密钥验证请求");
        
        // 创建Gemini模型提供商
        GeminiModelProvider provider = new GeminiModelProvider(
                request.getModel(), 
                request.getApiKey(),
                null);
        
        // 验证API密钥
        return provider.validateApiKey()
                .map(valid -> Map.of("valid", valid));
    }
    
    /**
     * Gemini测试请求
     */
    @Data
    public static class GeminiTestRequest {
        private String apiKey;
        private String model = "gemini-2.0-flash";
        private String prompt;
        private Double temperature = 0.7;
        private Integer maxTokens = 1000;
        private List<GeminiMessage> messages;
    }
    
    /**
     * Gemini消息
     */
    @Data
    public static class GeminiMessage {
        private String role;
        private String content;
    }
} 