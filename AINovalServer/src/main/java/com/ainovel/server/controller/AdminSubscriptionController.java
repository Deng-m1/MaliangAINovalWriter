package com.ainovel.server.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ainovel.server.common.response.ApiResponse;
import com.ainovel.server.domain.model.SubscriptionPlan;
import com.ainovel.server.service.SubscriptionPlanService;

import reactor.core.publisher.Mono;

/**
 * 管理员订阅计划管理控制器
 */
@RestController
@RequestMapping("/api/v1/admin/subscription-plans")
@PreAuthorize("hasAuthority('ADMIN_MANAGE_SUBSCRIPTIONS') or hasRole('SUPER_ADMIN')")
public class AdminSubscriptionController {
    
    private final SubscriptionPlanService subscriptionPlanService;
    
    @Autowired
    public AdminSubscriptionController(SubscriptionPlanService subscriptionPlanService) {
        this.subscriptionPlanService = subscriptionPlanService;
    }
    
    /**
     * 获取所有订阅计划
     *
     * 注意：前端期望在 data 字段中拿到 List<SubscriptionPlan>，
     * 因此前端管理端不要直接返回 Flux，而是 collectList 后再包装。
     */
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<java.util.List<SubscriptionPlan>>>> getAllPlans() {
        return subscriptionPlanService.findAll()
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list)))
                .onErrorResume(e -> Mono.just(
                    ResponseEntity.badRequest().body(ApiResponse.error("获取订阅计划失败: " + e.getMessage()))
                ));
    }
    
    /**
     * 根据ID获取订阅计划
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<SubscriptionPlan>>> getPlanById(@PathVariable String id) {
        return subscriptionPlanService.findById(id)
                .map(plan -> ResponseEntity.ok(ApiResponse.success(plan)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * 创建新订阅计划
     */
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<SubscriptionPlan>>> createPlan(@RequestBody SubscriptionPlan plan) {
        return subscriptionPlanService.createPlan(plan)
                .map(savedPlan -> ResponseEntity.ok(ApiResponse.success(savedPlan)))
                .onErrorResume(e -> Mono.just(
                    ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()))
                ));
    }
    
    /**
     * 更新订阅计划
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<SubscriptionPlan>>> updatePlan(@PathVariable String id, @RequestBody SubscriptionPlan plan) {
        return subscriptionPlanService.updatePlan(id, plan)
                .map(updatedPlan -> ResponseEntity.ok(ApiResponse.success(updatedPlan)))
                .onErrorResume(e -> Mono.just(
                    ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()))
                ));
    }
    
    /**
     * 删除订阅计划
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<Void>>> deletePlan(@PathVariable String id) {
        return subscriptionPlanService.deletePlan(id)
                .then(Mono.just(ResponseEntity.ok(ApiResponse.<Void>success())))
                .onErrorResume(e -> Mono.just(
                    ResponseEntity.badRequest().body(ApiResponse.<Void>error(e.getMessage()))
                ));
    }
    
    /**
     * 启用/禁用订阅计划
     */
    @PatchMapping("/{id}/status")
    public Mono<ResponseEntity<ApiResponse<SubscriptionPlan>>> togglePlanStatus(
            @PathVariable String id, 
            @RequestBody StatusRequest request) {
        return subscriptionPlanService.togglePlanStatus(id, request.isActive())
                .map(updatedPlan -> ResponseEntity.ok(ApiResponse.success(updatedPlan)))
                .onErrorResume(e -> Mono.just(
                    ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()))
                ));
    }
    
    /**
     * 状态请求DTO
     */
    public static class StatusRequest {
        private boolean active;
        
        public boolean isActive() {
            return active;
        }
        
        public void setActive(boolean active) {
            this.active = active;
        }
    }
}