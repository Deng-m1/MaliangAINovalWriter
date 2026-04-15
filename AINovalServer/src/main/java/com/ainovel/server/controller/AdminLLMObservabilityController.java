package com.ainovel.server.controller;

import com.ainovel.server.common.response.ApiResponse;
import com.ainovel.server.common.response.PagedResponse;
import com.ainovel.server.common.response.CursorPageResponse;

import com.ainovel.server.domain.model.observability.LLMTrace;
import com.ainovel.server.service.ai.observability.LLMTraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员LLM可观测性控制器
 * 用于查看和管理大模型调用日志，便于运维和观察
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/llm-observability")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员LLM可观测性", description = "大模型调用日志查看和分析")
public class AdminLLMObservabilityController {

    @Autowired
    private LLMTraceService llmTraceService;

    // ==================== 日志查询 ====================

    /**
     * 获取所有LLM调用日志
     */
    @GetMapping("/traces")
    @Operation(summary = "获取LLM调用日志", description = "分页获取系统中所有的LLM调用日志")
    public Mono<ResponseEntity<ApiResponse<PagedResponse<LLMTrace>>>> getAllTraces(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        log.info("管理员获取LLM调用日志: page={}, size={}, sortBy={}, sortDir={}", page, size, sortBy, sortDir);
        
        return llmTraceService.findAllTracesPageable(page, size)
                .map(pagedResponse -> ResponseEntity.ok(ApiResponse.success(pagedResponse)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("获取LLM调用日志失败")));
    }

    /**
     * 游标分页：按时间倒序滚动查询
     */
    @GetMapping("/traces/cursor")
    @Operation(summary = "游标分页获取LLM调用日志", description = "基于createdAt/_id倒序的游标分页，适合无限滚动")
    public Mono<ResponseEntity<ApiResponse<CursorPageResponse<LLMTrace>>>> getTracesByCursor(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) int limit,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Boolean hasError,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) LLMTrace.CallType type,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        log.info("管理员(游标)获取LLM调用日志: cursor={}, limit={}", cursor, limit);
        return llmTraceService.findTracesByCursor(cursor, limit, userId, provider, model, sessionId, hasError,
                        businessType, correlationId, traceId, type, tag, startTime, endTime)
                .map(result -> ResponseEntity.ok(ApiResponse.success(result)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("游标方式获取LLM调用日志失败")));
    }

    /**
     * 根据用户ID获取LLM调用日志
     */
    @GetMapping("/traces/user/{userId}")
    @Operation(summary = "获取用户LLM调用日志", description = "获取指定用户的所有LLM调用日志")
    public Mono<ResponseEntity<ApiResponse<PagedResponse<LLMTrace>>>> getTracesByUserId(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) int size) {
        log.info("管理员获取用户LLM调用日志: userId={}, page={}, size={}", userId, page, size);
        
        return llmTraceService.findTracesByUserIdPageable(userId, page, size)
                .map(pagedResponse -> ResponseEntity.ok(ApiResponse.success(pagedResponse)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("获取用户LLM调用日志失败")));
    }

    /**
     * 根据提供商获取LLM调用日志
     */
    @GetMapping("/traces/provider/{provider}")
    @Operation(summary = "获取提供商LLM调用日志", description = "获取指定提供商的所有LLM调用日志")
    public Mono<ResponseEntity<ApiResponse<PagedResponse<LLMTrace>>>> getTracesByProvider(
            @PathVariable String provider,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) int size) {
        log.info("管理员获取提供商LLM调用日志: provider={}, page={}, size={}", provider, page, size);
        
        return llmTraceService.findTracesByProviderPageable(provider, page, size)
                .map(pagedResponse -> ResponseEntity.ok(ApiResponse.success(pagedResponse)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("获取提供商LLM调用日志失败")));
    }

    /**
     * 根据模型名称获取LLM调用日志
     */
    @GetMapping("/traces/model/{modelName}")
    @Operation(summary = "获取模型LLM调用日志", description = "获取指定模型的所有LLM调用日志")
    public Mono<ResponseEntity<ApiResponse<PagedResponse<LLMTrace>>>> getTracesByModel(
            @PathVariable String modelName,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) int size) {
        log.info("管理员获取模型LLM调用日志: modelName={}, page={}, size={}", modelName, page, size);
        
        return llmTraceService.findTracesByModelPageable(modelName, page, size)
                .map(pagedResponse -> ResponseEntity.ok(ApiResponse.success(pagedResponse)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("获取模型LLM调用日志失败")));
    }

    /**
     * 根据时间范围获取LLM调用日志
     */
    @GetMapping("/traces/timerange")
    @Operation(summary = "按时间范围获取LLM调用日志", description = "获取指定时间范围内的LLM调用日志")
    public Mono<ResponseEntity<ApiResponse<PagedResponse<LLMTrace>>>> getTracesByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) int size) {
        log.info("管理员按时间范围获取LLM调用日志: startTime={}, endTime={}, page={}, size={}", 
                 startTime, endTime, page, size);
        
        return llmTraceService.findTracesByTimeRangePageable(startTime, endTime, page, size)
                .map(pagedResponse -> ResponseEntity.ok(ApiResponse.success(pagedResponse)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("按时间范围获取LLM调用日志失败")));
    }

    /**
     * 搜索LLM调用日志
     */
    @GetMapping("/traces/search")
    @Operation(summary = "搜索LLM调用日志", description = "根据多个条件搜索LLM调用日志")
    public Mono<ResponseEntity<ApiResponse<PagedResponse<LLMTrace>>>> searchTraces(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Boolean hasError,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) LLMTrace.CallType type,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) int size) {
        log.info("管理员搜索LLM调用日志: userId={}, provider={}, model={}, sessionId={}, hasError={}, businessType={}, correlationId={}, traceId={}, type={}, tag={}, page={}, size={}", 
                 userId, provider, model, sessionId, hasError, businessType, correlationId, traceId, type, tag, page, size);
        
        return llmTraceService.searchTracesPageable(
                userId, provider, model, sessionId, hasError, businessType, correlationId, traceId, type, tag, startTime, endTime, page, size)
                .map(pagedResponse -> ResponseEntity.ok(ApiResponse.success(pagedResponse)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("搜索LLM调用日志失败")));
    }

    /**
     * 导出LLM调用日志（应用过滤条件）
     */
    @PostMapping("/export2")
    @Operation(summary = "导出LLM调用日志(带过滤)", description = "导出指定条件的LLM调用日志，应用与search相同的过滤")
    public Mono<ResponseEntity<ApiResponse<List<LLMTrace>>>> exportTracesAdvanced(
            @RequestBody(required = false) Map<String, Object> filterCriteria,
            @AuthenticationPrincipal com.ainovel.server.security.CurrentUser currentUser) {
        String adminId = currentUser.getId();
        log.info("管理员 {} 导出LLM调用日志(高级)", adminId);

        String userId = asString(filterCriteria, "userId");
        String provider = asString(filterCriteria, "provider");
        String model = asString(filterCriteria, "model");
        String sessionId = asString(filterCriteria, "sessionId");
        Boolean hasError = asBoolean(filterCriteria, "hasError");
        String businessType = asString(filterCriteria, "businessType");
        String correlationId = asString(filterCriteria, "correlationId");
        String traceId = asString(filterCriteria, "traceId");
        LLMTrace.CallType type = asCallType(filterCriteria, "type");
        String tag = asString(filterCriteria, "tag");
        LocalDateTime startTime = asDateTime(filterCriteria, "startTime");
        LocalDateTime endTime = asDateTime(filterCriteria, "endTime");

        return llmTraceService.filterAll(userId, provider, model, sessionId, hasError, businessType,
                        correlationId, traceId, type, tag, startTime, endTime)
                .map(traces -> ResponseEntity.ok(ApiResponse.success(traces)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("导出日志失败")));
    }

    private static String asString(Map<String, Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }
    private static Boolean asBoolean(Map<String, Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        if (v == null) return null;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(v.toString());
    }
    private static LocalDateTime asDateTime(Map<String, Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        if (v == null) return null;
        try { return LocalDateTime.parse(v.toString()); } catch (Exception e) { return null; }
    }
    private static LLMTrace.CallType asCallType(Map<String, Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        if (v == null) return null;
        try { return LLMTrace.CallType.valueOf(v.toString()); } catch (Exception e) { return null; }
    }

    /**
     * 获取单个LLM调用日志详情
     */
    @GetMapping("/traces/{traceId}")
    @Operation(summary = "获取LLM调用日志详情", description = "获取指定ID的LLM调用日志详细信息")
    public Mono<ResponseEntity<ApiResponse<LLMTrace>>> getTraceById(@PathVariable String traceId) {
        log.info("管理员获取LLM调用日志详情: {}", traceId);
        
        return llmTraceService.findTraceById(traceId)
                .map(trace -> ResponseEntity.ok(ApiResponse.success(trace)))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("日志不存在")));
    }

    // ==================== 统计分析 ====================

    /**
     * 获取LLM调用统计信息
     */
    @GetMapping("/statistics/overview")
    @Operation(summary = "获取LLM调用统计概览", description = "获取系统LLM调用的统计概览信息")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> getOverviewStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("获取LLM调用统计概览: startTime={}, endTime={}", startTime, endTime);
        
        return llmTraceService.getOverviewStatistics(startTime, endTime)
                .map(stats -> ResponseEntity.ok(ApiResponse.success(stats)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("获取统计信息失败")));
    }

    /**
     * 获取提供商统计信息
     */
    @GetMapping("/statistics/providers")
    @Operation(summary = "获取提供商统计信息", description = "获取各提供商的调用统计信息")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> getProviderStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("获取提供商统计信息: startTime={}, endTime={}", startTime, endTime);
        
        return llmTraceService.getProviderStatistics(startTime, endTime)
                .map(stats -> ResponseEntity.ok(ApiResponse.success(stats)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("获取提供商统计失败")));
    }

    /**
     * 获取模型统计信息
     */
    @GetMapping("/statistics/models")
    @Operation(summary = "获取模型统计信息", description = "获取各模型的调用统计信息")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> getModelStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("获取模型统计信息: startTime={}, endTime={}", startTime, endTime);
        
        return llmTraceService.getModelStatistics(startTime, endTime)
                .map(stats -> ResponseEntity.ok(ApiResponse.success(stats)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("获取模型统计失败")));
    }

    /**
     * 获取用户统计信息
     */
    @GetMapping("/statistics/users")
    @Operation(summary = "获取用户统计信息", description = "获取用户LLM调用统计信息")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> getUserStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("获取用户统计信息: startTime={}, endTime={}", startTime, endTime);
        
        return llmTraceService.getUserStatistics(startTime, endTime)
                .map(stats -> ResponseEntity.ok(ApiResponse.success(stats)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("获取用户统计失败")));
    }

    /**
     * 获取错误统计信息
     */
    @GetMapping("/statistics/errors")
    @Operation(summary = "获取错误统计信息", description = "获取LLM调用错误的统计信息")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> getErrorStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("获取错误统计信息: startTime={}, endTime={}", startTime, endTime);
        
        return llmTraceService.getErrorStatistics(startTime, endTime)
                .map(stats -> ResponseEntity.ok(ApiResponse.success(stats)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("获取错误统计失败")));
    }

    /**
     * 获取性能统计信息
     */
    @GetMapping("/statistics/performance")
    @Operation(summary = "获取性能统计信息", description = "获取LLM调用性能统计信息")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> getPerformanceStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("获取性能统计信息: startTime={}, endTime={}", startTime, endTime);
        
        return llmTraceService.getPerformanceStatistics(startTime, endTime)
                .map(stats -> ResponseEntity.ok(ApiResponse.success(stats)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("获取性能统计失败")));
    }

    /**
     * 获取趋势数据（按时间分桶）
     */
    @GetMapping("/statistics/trends")
    @Operation(summary = "获取趋势数据", description = "按时间分桶返回指定指标的趋势数据")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> getTrends(
            @RequestParam(required = false) String metric,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String provider,
            @RequestParam(defaultValue = "hour") String interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("获取趋势数据 metric={}, groupBy={}, businessType={}, model={}, provider={}, interval={}, startTime={}, endTime={}",
                metric, groupBy, businessType, model, provider, interval, startTime, endTime);

        return llmTraceService.getTrends(metric, groupBy, businessType, model, provider, interval, startTime, endTime)
                .map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("获取趋势数据失败")));
    }

    /**
     * 获取指定用户的功能维度统计（按业务功能聚合调用次数与Token）
     */
    @GetMapping("/statistics/users/{userId}/features")
    @Operation(summary = "获取用户功能维度统计", description = "按业务功能聚合调用次数与Token")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> getUserFeatureStatistics(
            @PathVariable String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return llmTraceService.getUserFeatureStatistics(userId, startTime, endTime)
                .map(stats -> ResponseEntity.ok(ApiResponse.success(stats)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("获取用户功能维度统计失败")));
    }

    /**
     * 获取指定用户日维度Token消耗
     */
    @GetMapping("/statistics/users/{userId}/daily-tokens")
    @Operation(summary = "获取用户日维度Token消耗", description = "按天统计Token消耗")
    public Mono<ResponseEntity<ApiResponse<Map<String, Integer>>>> getUserDailyTokens(
            @PathVariable String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return llmTraceService.getUserDailyTokens(userId, startTime, endTime)
                .map(stats -> ResponseEntity.ok(ApiResponse.success(stats)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("获取用户日维度Token统计失败")));
    }

    // ==================== 导出功能 ====================

    /**
     * 导出LLM调用日志
     */
    @PostMapping("/export")
    @Operation(summary = "导出LLM调用日志", description = "导出指定条件的LLM调用日志")
    public Mono<ResponseEntity<ApiResponse<List<LLMTrace>>>> exportTraces(
            @RequestBody(required = false) Map<String, Object> filterCriteria,
            @AuthenticationPrincipal com.ainovel.server.security.CurrentUser currentUser) {
        String adminId = currentUser.getId();
        log.info("管理员 {} 导出LLM调用日志", adminId);
        
        return llmTraceService.exportTraces(filterCriteria)
                .map(traces -> ResponseEntity.ok(ApiResponse.success(traces)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("导出日志失败")));
    }

    // ==================== 系统管理 ====================

    /**
     * 清理旧日志
     */
    @DeleteMapping("/cleanup")
    @Operation(summary = "清理旧日志", description = "清理指定时间之前的LLM调用日志")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> cleanupOldTraces(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beforeTime,
            @AuthenticationPrincipal com.ainovel.server.security.CurrentUser currentUser) {
        String adminId = currentUser.getId();
        log.info("管理员 {} 清理{}之前的LLM调用日志", adminId, beforeTime);
        
        return llmTraceService.cleanupOldTraces(beforeTime)
                .map(result -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("deletedCount", result);
                    response.put("beforeTime", beforeTime);
                    return ResponseEntity.ok(ApiResponse.success(response));
                })
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("清理日志失败")));
    }

    /**
     * 获取系统健康状态
     */
    @GetMapping("/health")
    @Operation(summary = "获取系统健康状态", description = "获取LLM可观测性系统的健康状态")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> getSystemHealth() {
        log.info("获取LLM可观测性系统健康状态");
        
        return llmTraceService.getSystemHealth()
                .map(health -> ResponseEntity.ok(ApiResponse.success(health)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("获取系统健康状态失败")));
    }

    /**
     * 获取数据库状态
     */
    @GetMapping("/database/status")
    @Operation(summary = "获取数据库状态", description = "获取LLM日志数据库的状态信息")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> getDatabaseStatus() {
        log.info("获取LLM日志数据库状态");
        
        return llmTraceService.getDatabaseStatus()
                .map(status -> ResponseEntity.ok(ApiResponse.success(status)))
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("获取数据库状态失败")));
    }
}