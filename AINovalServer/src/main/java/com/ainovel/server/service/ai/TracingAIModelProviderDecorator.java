package com.ainovel.server.service.ai;

import com.ainovel.server.domain.model.AIRequest;
import com.ainovel.server.domain.model.AIResponse;
import com.ainovel.server.domain.model.ModelInfo;
import com.ainovel.server.domain.model.observability.LLMTrace;
import com.ainovel.server.service.ai.capability.ToolCallCapable;
import com.ainovel.server.service.ai.observability.TraceContextManager;
import com.ainovel.server.service.ai.observability.events.LLMTraceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AIModelProvider的追踪装饰器
 * 实现了装饰器模式，为任何AIModelProvider实例动态添加LLM可观测性追踪功能。
 * 这个类包含了原本在AIModelProviderTraceAspect中的所有追踪逻辑。
 * 
 * 通过条件实现ToolCallCapable接口，保持装饰器的透明性：
 * - 如果被装饰对象支持工具调用，装饰器也会支持
 * - 使用策略模式避免强制类型转换的问题
 */
@Slf4j
@RequiredArgsConstructor
public class TracingAIModelProviderDecorator implements AIModelProvider, ToolCallCapable {

    private final AIModelProvider decoratedProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final TraceContextManager traceContextManager;
    /**
     * 标记当前提供者是否为基于 LangChain4j 的实现。
     * 若为 true：非流式场景下由 RichTraceChatModelListener 统一发布事件，装饰器不再发布，避免重复。
     * 若为 false：由装饰器在非流式场景发布事件，作为非 LangChain4j 场景兜底。
     */
    private final boolean isLangChain4jProvider;

    @Override
    public Mono<AIResponse> generateContent(AIRequest request) {
        Instant startTime = Instant.now();
        
        // 1. 创建LLMTrace对象（从切面逻辑转移）
        // 优先使用业务侧透传的idempotencyKey作为traceId，保证与预扣费记录一致
        String requestTraceId = extractIdFromRequestOrIdempotencyKey(request);
        if (requestTraceId == null || requestTraceId.isBlank()) {
            requestTraceId = UUID.randomUUID().toString();
        }
        LLMTrace trace = LLMTrace.fromRequest(
                requestTraceId,
                getProviderName(),
                getModelName(),
                request
        );
        
        // 从业务上下文获取关联ID（如果有）
        String correlationId = extractCorrelationId(request);
        if (correlationId != null) {
            trace.setCorrelationId(correlationId);
        }

        trace.getRequest().setTimestamp(startTime);
        trace.getPerformance().setRequestLatencyMs(Duration.between(startTime, Instant.now()).toMillis());

        // 🚀 关键修复：在发起HTTP请求之前就存储trace，确保ChatModelListener能够获取到
        traceContextManager.setTrace(trace);
        log.debug("✅ 提前存储trace到上下文，供ChatModelListener使用: traceId={}, threadName={}", 
                 trace.getTraceId(), Thread.currentThread().getName());

        // 2. 执行原始方法并追踪Mono响应（从切面逻辑转移）
        return traceMonoResponse(decoratedProvider.generateContent(request), trace, startTime);
    }

    @Override
    public Flux<String> generateContentStream(AIRequest request) {
        Instant startTime = Instant.now();
        
        // 1. 创建LLMTrace对象（从切面逻辑转移）
        // 优先使用业务侧透传的idempotencyKey作为traceId，保证与预扣费记录一致
        String requestTraceId = extractIdFromRequestOrIdempotencyKey(request);
        if (requestTraceId == null || requestTraceId.isBlank()) {
            requestTraceId = UUID.randomUUID().toString();
        }
        LLMTrace trace = LLMTrace.fromRequest(
                requestTraceId,
                getProviderName(),
                getModelName(),
                request
        );
        
        // 从业务上下文获取关联ID（如果有）
        String correlationId = extractCorrelationId(request);
        if (correlationId != null) {
            trace.setCorrelationId(correlationId);
        }

        trace.getRequest().setTimestamp(startTime);
        trace.getPerformance().setRequestLatencyMs(Duration.between(startTime, Instant.now()).toMillis());
        trace.setStreamingType(); // 标记为流式调用

        // 🚀 关键修复：在发起HTTP请求之前就存储trace，确保ChatModelListener能够获取到
        traceContextManager.setTrace(trace);
        log.debug("✅ 提前存储trace到上下文，供ChatModelListener使用: traceId={}, threadName={}", 
                 trace.getTraceId(), Thread.currentThread().getName());

        // 2. 执行原始方法并追踪Flux响应（从切面逻辑转移）
        return traceFluxResponse(decoratedProvider.generateContentStream(request), trace, startTime);
    }

    /**
     * 追踪Mono响应（非流式）
     * 从AIModelProviderTraceAspect.traceMonoResponse方法完整转移
     */
    private Mono<AIResponse> traceMonoResponse(Mono<AIResponse> original, LLMTrace trace, Instant startTime) {
        // 注意：trace已经在generateContent中提前存储到TraceContextManager了
        
        return original
                .contextWrite(ctx -> ctx.put(LLMTrace.class, trace)) // 保持Reactor Context注入（兼容性）
                .doOnSuccess(response -> {
                    try {
                        Instant endTime = Instant.now();
                        trace.setResponseFromAIResponse(response, endTime);
                        trace.getPerformance().setTotalDurationMs(Duration.between(startTime, endTime).toMillis());
                        // 非流式：仅在非 LangChain4j 场景由装饰器发布，LangChain4j 交由监听器统一发布
                        if (!isLangChain4jProvider) {
                            log.info("📤 非流式-非LangChain4j：装饰器发布事件: traceId={}, type={}", trace.getTraceId(), trace.getType());
                            publishTraceEvent(trace);
                        } else {
                            log.info("🔄 非流式-LangChain4j：装饰器跳过发布，交由监听器处理: traceId={}, type={}", trace.getTraceId(), trace.getType());
                        }
                    } finally {
                        // 清理trace上下文
                        traceContextManager.clearTrace();
                    }
                })
                .doOnError(error -> {
                    try {
                        Instant endTime = Instant.now();
                        trace.setErrorFromThrowable(error, endTime);
                        trace.getPerformance().setTotalDurationMs(Duration.between(startTime, endTime).toMillis());
                        if (!isLangChain4jProvider) {
                            log.info("📤 非流式错误-非LangChain4j：装饰器发布事件: traceId={}, type={}", trace.getTraceId(), trace.getType());
                            publishTraceEvent(trace);
                        } else {
                            log.info("🔄 非流式错误-LangChain4j：装饰器跳过发布，交由监听器处理: traceId={}, type={}", trace.getTraceId(), trace.getType());
                        }
                    } finally {
                        // 清理trace上下文
                        traceContextManager.clearTrace();
                    }
                });
    }

    /**
     * 追踪Flux响应（流式）
     * 从AIModelProviderTraceAspect.traceFluxResponse方法完整转移，增加token信息获取
     */
    private Flux<String> traceFluxResponse(Flux<String> original, LLMTrace trace, Instant startTime) {
        AtomicReference<Instant> firstChunkTime = new AtomicReference<>();
        StringBuilder contentBuffer = new StringBuilder();

        // 注意：trace已经在generateContentStream中提前存储到TraceContextManager了

        return original
                .contextWrite(ctx -> ctx.put(LLMTrace.class, trace)) // 保持Reactor Context注入（兼容性）
                .doOnNext(content -> {
                    // 记录首个token时间
                    if (firstChunkTime.get() == null && !"heartbeat".equals(content)) {
                        firstChunkTime.set(Instant.now());
                        trace.getPerformance().setFirstTokenLatencyMs(
                                Duration.between(startTime, firstChunkTime.get()).toMillis());
                    }
                    
                    // 累积内容（过滤心跳信号）
                    if (!"heartbeat".equals(content)) {
                        contentBuffer.append(content);
                    }
                })
                .doOnComplete(() -> {
                    try {
                        Instant endTime = Instant.now();
                        
                        // 在覆盖响应前，暂存监听器已写入的元数据（尤其是tokenUsage）
                        LLMTrace.TokenUsageInfo preservedTokenUsage = null;
                        String preservedId = null;
                        String preservedFinishReason = null;
                        if (trace.getResponse() != null && trace.getResponse().getMetadata() != null) {
                            preservedTokenUsage = trace.getResponse().getMetadata().getTokenUsage();
                            preservedId = trace.getResponse().getMetadata().getId();
                            preservedFinishReason = trace.getResponse().getMetadata().getFinishReason();
                        }

                        // 🚀 让RichTraceChatModelListener提供tokenUsage，但避免被覆盖
                        trace.setResponseFromStreamingResult(contentBuffer.toString(), endTime);
                        // 恢复被监听器写入的元数据
                        if (trace.getResponse() != null && trace.getResponse().getMetadata() != null) {
                            if (preservedId != null && (trace.getResponse().getMetadata().getId() == null)) {
                                trace.getResponse().getMetadata().setId(preservedId);
                            }
                            // 优先保留监听器写入的finishReason（一般为STOP），否则沿用默认stop
                            if (preservedFinishReason != null && !preservedFinishReason.isEmpty()) {
                                trace.getResponse().getMetadata().setFinishReason(preservedFinishReason);
                            }
                            if (preservedTokenUsage != null) {
                                trace.getResponse().getMetadata().setTokenUsage(preservedTokenUsage);
                            }
                        }
                        trace.getPerformance().setTotalDurationMs(Duration.between(startTime, endTime).toMillis());
                        // 🚀 流式：由装饰器在完成时发布事件（Listener已提前增强tokenUsage）
                        log.info("📤 流式请求完成：装饰器发布事件: traceId={}, type={}", trace.getTraceId(), trace.getType());
                        publishTraceEvent(trace);
                        log.info("✅ 流式响应完成，已发布事件: traceId={}", trace.getTraceId());
                    } finally {
                        // 🚀 由装饰器负责清理上下文
                        traceContextManager.clearTrace();
                        log.debug("流式响应完成，已清理trace上下文: traceId={}", trace.getTraceId());
                    }
                })
                .doOnError(error -> {
                    try {
                        Instant endTime = Instant.now();
                        trace.setErrorFromThrowable(error, endTime);
                        trace.getPerformance().setTotalDurationMs(Duration.between(startTime, endTime).toMillis());
                        // 🚀 流式错误：由装饰器发布错误事件
                        publishTraceEvent(trace);
                        log.debug("流式响应出错，已发布错误事件: traceId={}, error={}", trace.getTraceId(), error.getMessage());
                    } finally {
                        // 🚀 由装饰器负责清理上下文
                        traceContextManager.clearTrace();
                        log.debug("流式响应出错，已清理trace上下文: traceId={}", trace.getTraceId());
                    }
                })
                .doOnCancel(() -> {
                    try {
                        // 处理取消情况
                        Instant endTime = Instant.now();
                        if (contentBuffer.length() > 0) {
                            // 如果已经有内容，记录部分响应
                            // 在覆盖响应前，暂存监听器已写入的tokenUsage
                            LLMTrace.TokenUsageInfo preservedTokenUsage = null;
                            String preservedId = null;
                            String preservedFinishReason = null;
                            if (trace.getResponse() != null && trace.getResponse().getMetadata() != null) {
                                preservedTokenUsage = trace.getResponse().getMetadata().getTokenUsage();
                                preservedId = trace.getResponse().getMetadata().getId();
                                preservedFinishReason = trace.getResponse().getMetadata().getFinishReason();
                            }

                            trace.setResponseFromStreamingResult(contentBuffer.toString(), endTime);
                            if (trace.getResponse() != null && trace.getResponse().getMetadata() != null) {
                                if (preservedId != null && (trace.getResponse().getMetadata().getId() == null)) {
                                    trace.getResponse().getMetadata().setId(preservedId);
                                }
                                if (preservedFinishReason != null && !preservedFinishReason.isEmpty()) {
                                    trace.getResponse().getMetadata().setFinishReason(preservedFinishReason);
                                }
                                if (preservedTokenUsage != null) {
                                    trace.getResponse().getMetadata().setTokenUsage(preservedTokenUsage);
                                }
                            }
                            trace.getResponse().getMetadata().setFinishReason("cancelled");
                        }
                        trace.getPerformance().setTotalDurationMs(Duration.between(startTime, endTime).toMillis());
                        // 🚀 流式取消：由装饰器发布事件
                        publishTraceEvent(trace);
                        log.debug("流式响应被取消，已发布事件: traceId={}", trace.getTraceId());
                    } finally {
                        // 🚀 由装饰器负责清理上下文
                        traceContextManager.clearTrace();
                        log.debug("流式响应被取消，已清理trace上下文: traceId={}", trace.getTraceId());
                    }
                });
    }

    /**
     * 从请求中提取关联ID
     * 从AIModelProviderTraceAspect.extractCorrelationId方法完整转移
     */
    private String extractCorrelationId(AIRequest request) {
        // 从metadata中提取关联ID
        if (request.getMetadata() != null) {
            Object correlationId = request.getMetadata().get("correlationId");
            if (correlationId != null) {
                return correlationId.toString();
            }
        }
        
        // 或者基于业务字段生成关联ID
        if (request.getNovelId() != null && request.getSceneId() != null) {
            return String.format("%s-%s", request.getNovelId(), request.getSceneId());
        }
        
        return null;
    }

    /**
     * 从AIRequest中提取幂等键作为traceId（业务层通过BillingMarkerEnricher注入）
     */
    private String extractIdFromRequestOrIdempotencyKey(AIRequest request) {
        try {
            // 优先使用显式 traceId 字段
            if (request.getTraceId() != null && !request.getTraceId().isBlank()) {
                return request.getTraceId();
            }
            if (request.getParameters() != null) {
                Object psRaw = request.getParameters().get("providerSpecific");
                if (psRaw instanceof java.util.Map<?, ?> m) {
                    Object key = m.get(com.ainovel.server.service.billing.BillingKeys.REQUEST_IDEMPOTENCY_KEY);
                    if (key != null) {
                        return key.toString();
                    }
                }
            }
            if (request.getMetadata() != null) {
                Object key = request.getMetadata().get(com.ainovel.server.service.billing.BillingKeys.REQUEST_IDEMPOTENCY_KEY);
                if (key != null) {
                    return key.toString();
                }
            }
        } catch (Exception ignore) {}
        return null;
    }

    /**
     * 发布追踪事件
     * 从AIModelProviderTraceAspect.publishTraceEvent方法完整转移
     */
    private void publishTraceEvent(LLMTrace trace) {
        try {
            log.info("🎯 装饰器即将发布LLMTraceEvent: traceId={}, type={}, isLangChain4j={}", 
                    trace.getTraceId(), trace.getType(), isLangChain4jProvider);
            eventPublisher.publishEvent(new LLMTraceEvent(this, trace));
            log.info("🎯 装饰器已发布LLM追踪事件: traceId={}", trace.getTraceId());
        } catch (Exception e) {
            log.error("发布LLM追踪事件失败: traceId={}", trace.getTraceId(), e);
        }
    }

    // --- 其他接口方法直接委托给被装饰对象 ---

    @Override
    public String getProviderName() {
        return decoratedProvider.getProviderName();
    }

    @Override
    public String getModelName() {
        return decoratedProvider.getModelName();
    }

    @Override
    public Mono<Double> estimateCost(AIRequest request) {
        return decoratedProvider.estimateCost(request);
    }

    @Override
    public Mono<Boolean> validateApiKey() {
        return decoratedProvider.validateApiKey();
    }

    @Override
    public void setProxy(String host, int port) {
        decoratedProvider.setProxy(host, port);
    }

    @Override
    public void setTrustAllCerts(boolean trustAllCerts) {
        decoratedProvider.setTrustAllCerts(trustAllCerts);
    }

    @Override
    public void disableProxy() {
        decoratedProvider.disableProxy();
    }

    @Override
    public boolean isProxyEnabled() {
        return decoratedProvider.isProxyEnabled();
    }

    @Override
    public Flux<ModelInfo> listModels() {
        return decoratedProvider.listModels();
    }

    @Override
    public Flux<ModelInfo> listModelsWithApiKey(String apiKey, String apiEndpoint) {
        return decoratedProvider.listModelsWithApiKey(apiKey, apiEndpoint);
    }

    @Override
    public String getApiKey() {
        return decoratedProvider.getApiKey();
    }

    @Override
    public String getApiEndpoint() {
        return decoratedProvider.getApiEndpoint();
    }
    
    // ====== ToolCallCapable 条件实现 ======
    
    /**
     * 检查是否支持工具调用
     * 委托给被装饰的对象进行判断
     */
    @Override
    public boolean supportsToolCalling() {
        if (decoratedProvider instanceof ToolCallCapable toolCallCapable) {
            return toolCallCapable.supportsToolCalling();
        }
        return false;
    }
    
    /**
     * 获取支持工具调用的聊天模型
     * 如果被装饰对象支持工具调用，则委托调用；否则抛出异常
     */
    @Override
    public ChatLanguageModel getToolCallableChatModel() {
        if (decoratedProvider instanceof ToolCallCapable toolCallCapable) {
            return toolCallCapable.getToolCallableChatModel();
        }
        throw new UnsupportedOperationException(
            "被装饰的提供者 " + decoratedProvider.getClass().getSimpleName() + " 不支持工具调用");
    }
    
    /**
     * 获取支持工具调用的流式聊天模型
     * 如果被装饰对象支持工具调用，则委托调用；否则返回null
     */
    @Override
    public StreamingChatLanguageModel getToolCallableStreamingChatModel() {
        if (decoratedProvider instanceof ToolCallCapable toolCallCapable) {
            return toolCallCapable.getToolCallableStreamingChatModel();
        }
        return null;
    }
} 