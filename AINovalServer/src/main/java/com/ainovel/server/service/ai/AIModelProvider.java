package com.ainovel.server.service.ai;

import com.ainovel.server.domain.model.AIRequest;
import com.ainovel.server.domain.model.AIResponse;
import com.ainovel.server.domain.model.ModelInfo;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AI模型提供商接口
 */
public interface AIModelProvider {

    /**
     * 获取提供商名称
     * @return 提供商名称
     */
    String getProviderName();

    /**
     * 获取模型名称
     * @return 模型名称
     */
    String getModelName();

    /**
     * 生成内容（非流式）
     * @param request AI请求
     * @return AI响应
     */
    Mono<AIResponse> generateContent(AIRequest request);

    /**
     * 生成内容（流式）
     * @param request AI请求
     * @return 流式AI响应
     */
    Flux<String> generateContentStream(AIRequest request);

    /**
     * 估算请求成本
     * @param request AI请求
     * @return 估算成本（单位：元）
     */
    Mono<Double> estimateCost(AIRequest request);

    /**
     * 检查API密钥是否有效
     * @return 是否有效
     */
    Mono<Boolean> validateApiKey();

    /**
     * 设置HTTP代理
     * @param host 代理主机
     * @param port 代理端口
     */
    void setProxy(String host, int port);

    /**
     * 设置是否信任所有证书（仅限排障时临时开启）
     * @param trustAllCerts 是否信任所有证书
     */
    void setTrustAllCerts(boolean trustAllCerts);

    /**
     * 禁用HTTP代理
     */
    void disableProxy();

    /**
     * 检查代理是否已启用
     * @return 是否已启用
     */
    boolean isProxyEnabled();

    /**
     * 获取提供商支持的模型列表
     * 不需要API密钥的提供商应该实现此方法以返回可用模型列表
     * 需要API密钥的提供商应该实现 listModelsWithApiKey 方法
     *
     * @return 模型信息列表
     */
    default Flux<ModelInfo> listModels() {
        // 默认实现返回空列表
        return Flux.empty();
    }

    /**
     * 使用API密钥获取提供商支持的模型列表
     * 需要API密钥的提供商应该实现此方法
     *
     * @param apiKey API密钥
     * @param apiEndpoint 可选的API端点
     * @return 模型信息列表
     */
    default Flux<ModelInfo> listModelsWithApiKey(String apiKey, String apiEndpoint) {
        // 默认实现返回空列表
        return Flux.empty();
    }

    /**
     * 获取API密钥
     * @return API密钥
     */
    String getApiKey();

    /**
     * 获取API端点
     * @return API端点
     */
    String getApiEndpoint();
}