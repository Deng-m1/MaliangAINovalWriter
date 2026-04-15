package com.ainovel.server.service.ai;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ainovel.server.domain.model.AIRequest;
import com.ainovel.server.domain.model.AIResponse;
import com.ainovel.server.domain.model.AIResponse.TokenUsage;

import lombok.Getter;
import reactor.core.publisher.Mono;

/**
 * 抽象AI模型提供商基类
 */
public abstract class AbstractAIModelProvider implements AIModelProvider {
    
    @Getter
    protected final String providerName;
    
    @Getter
    protected final String modelName;
    
    protected final String apiKey;
    
    protected final String apiEndpoint;
    
    // 代理配置
    @Getter
    protected String proxyHost;
    
    @Getter
    protected int proxyPort;
    
    protected boolean proxyEnabled;
    
    // Whether to skip TLS certificate verification (only for debugging proxies)
    protected boolean trustAllCerts;
    
    /**
     * 构造函数
     * @param providerName 提供商名称
     * @param modelName 模型名称
     * @param apiKey API密钥
     * @param apiEndpoint API端点
     */
    protected AbstractAIModelProvider(String providerName, String modelName, String apiKey, String apiEndpoint) {
        this.providerName = providerName;
        this.modelName = modelName;
        this.apiKey = apiKey;
        this.apiEndpoint = apiEndpoint;
        this.proxyEnabled = false;
    }
    
    /**
     * 设置HTTP代理
     * @param host 代理主机
     * @param port 代理端口
     */
    public void setProxy(String host, int port) {
        this.proxyHost = host;
        this.proxyPort = port;
        this.proxyEnabled = true;
    }
    
    /**
     * 设置是否信任所有证书（仅限排障时临时开启）
     * @param trustAllCerts 是否信任所有证书
     */
    public void setTrustAllCerts(boolean trustAllCerts) {
        this.trustAllCerts = trustAllCerts;
    }
    
    /**
     * 禁用HTTP代理
     */
    public void disableProxy() {
        this.proxyEnabled = false;
    }
    
    /**
     * 检查代理是否已启用
     * @return 是否已启用
     */
    @Override
    public boolean isProxyEnabled() {
        return proxyEnabled;
    }
    
    /**
     * 创建基础AI响应
     * @param content 内容
     * @param request 请求
     * @return AI响应
     */
    protected AIResponse createBaseResponse(String content, AIRequest request) {
        AIResponse response = new AIResponse();
        response.setId(UUID.randomUUID().toString());
        response.setModel(getModelName());
        response.setContent(content);
        response.setCreatedAt(LocalDateTime.now());
        response.setTokenUsage(new TokenUsage());
        return response;
    }
    
    /**
     * 检查API密钥是否为空
     * @return 是否为空
     */
    protected boolean isApiKeyEmpty() {
        return apiKey == null || apiKey.trim().isEmpty();
    }
    
    /**
     * 获取API端点
     * @param defaultEndpoint 默认端点
     * @return 实际使用的端点
     */
    protected String getApiEndpoint(String defaultEndpoint) {
        return apiEndpoint != null && !apiEndpoint.trim().isEmpty() ? apiEndpoint : defaultEndpoint;
    }
    
    /**
     * 处理API调用异常
     * @param e 异常
     * @param request 请求
     * @return 错误响应
     */
    protected Mono<AIResponse> handleApiException(Throwable e, AIRequest request) {
        AIResponse errorResponse = createBaseResponse("API调用失败: " + e.getMessage(), request);
        errorResponse.setFinishReason("error");
        return Mono.just(errorResponse);
    }
    
    /**
     * 获取API密钥
     * @return API密钥
     */
    @Override
    public String getApiKey() {
        return apiKey;
    }
    
    /**
     * 获取API端点
     * @return API端点
     */
    @Override
    public String getApiEndpoint() {
        return apiEndpoint;
    }
} 