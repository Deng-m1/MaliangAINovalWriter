package com.ainovel.server.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ainovel.server.common.response.ApiResponse;
import com.ainovel.server.domain.model.ModelInfo;
import com.ainovel.server.service.AIService;
import com.ainovel.server.service.ai.pricing.PricingDataSyncService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 管理员提供商和模型信息控制器
 * 用于获取可用的AI提供商和模型信息，以及同步定价数据
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/providers")
@PreAuthorize("hasAuthority('ADMIN_MANAGE_MODELS') or hasRole('SUPER_ADMIN')")
public class AdminProviderController {
    
    private final AIService aiService;
    private final PricingDataSyncService pricingDataSyncService;
    
    @Autowired
    public AdminProviderController(AIService aiService, PricingDataSyncService pricingDataSyncService) {
        this.aiService = aiService;
        this.pricingDataSyncService = pricingDataSyncService;
    }
    
    /**
     * 获取所有可用的提供商
     */
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<String>>>> getAvailableProviders() {
        return aiService.getAvailableProviders()
                .collectList()
                .map(providers -> ResponseEntity.ok(ApiResponse.success(providers)))
                .onErrorResume(e -> {
                    log.error("获取可用提供商失败", e);
                    return Mono.just(ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage())));
                });
    }
    
    /**
     * 获取指定提供商的模型信息
     */
    @GetMapping("/{provider}/models")
    public Mono<ResponseEntity<ApiResponse<List<ModelInfo>>>> getModelsForProvider(@PathVariable String provider) {
        return aiService.getModelInfosForProvider(provider)
                .collectList()
                .map(models -> ResponseEntity.ok(ApiResponse.success(models)))
                .onErrorResume(e -> {
                    log.error("获取提供商模型信息失败: {}", provider, e);
                    return Mono.just(ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage())));
                });
    }
    
    /**
     * 使用API Key获取指定提供商的模型信息
     */
    @PostMapping("/{provider}/models")
    public Mono<ResponseEntity<ApiResponse<List<ModelInfo>>>> getModelsWithApiKey(
            @PathVariable String provider,
            @RequestBody ApiKeyRequest request) {
        if (request.getApiKey() == null || request.getApiKey().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(ApiResponse.error("API Key不能为空")));
        }
        return aiService.getModelInfosForProviderWithApiKey(provider, request.getApiKey(), request.getApiEndpoint())
                .collectList()
                .map(models -> ResponseEntity.ok(ApiResponse.success(models)))
                .onErrorResume(e -> {
                    log.error("使用API Key获取提供商模型信息失败: {}", provider, e);
                    return Mono.just(ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage())));
                });
    }
    
    /**
     * 同步所有提供商的定价数据
     */
    @PostMapping("/sync-pricing")
    public Mono<ResponseEntity<ApiResponse<String>>> syncAllProvidersPricing() {
        return pricingDataSyncService.syncAllProvidersPricing()
                .then(Mono.just(ResponseEntity.ok(ApiResponse.success("定价数据同步已启动"))))
                .onErrorResume(e -> {
                    log.error("同步定价数据失败", e);
                    return Mono.just(ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage())));
                });
    }
    
    /**
     * 同步指定提供商的定价数据
     */
    @PostMapping("/{provider}/sync-pricing")
    public Mono<ResponseEntity<ApiResponse<String>>> syncProviderPricing(@PathVariable String provider) {
        return pricingDataSyncService.syncProviderPricing(provider)
                .then(Mono.just(ResponseEntity.ok(ApiResponse.success("提供商 " + provider + " 的定价数据同步已启动"))))
                .onErrorResume(e -> {
                    log.error("同步提供商定价数据失败: {}", provider, e);
                    return Mono.just(ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage())));
                });
    }
    
    /**
     * API Key请求DTO
     */
    public static class ApiKeyRequest {
        private String apiKey;
        private String apiEndpoint;
        
        public String getApiKey() {
            return apiKey;
        }
        
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
        
        public String getApiEndpoint() {
            return apiEndpoint;
        }
        
        public void setApiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
        }
    }
}