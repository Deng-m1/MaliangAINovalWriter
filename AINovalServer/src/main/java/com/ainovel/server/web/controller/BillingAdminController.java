package com.ainovel.server.web.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ainovel.server.common.response.ApiResponse;
import com.ainovel.server.domain.model.billing.CreditTransaction;
import com.ainovel.server.repository.CreditTransactionRepository;
import com.ainovel.server.service.billing.ReversalService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 管理员计费管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/billing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class BillingAdminController {

    private final CreditTransactionRepository txRepo;
    private final ReversalService reversalService;

    @GetMapping(value = "/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<List<CreditTransaction>>>> listTransactions(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userId) {
        int safeSize = Math.min(size, 100);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, safeSize);

        if (status != null && userId != null) {
            return txRepo.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable)
                    .collectList()
                    .map(list -> ResponseEntity.ok(ApiResponse.success(list)))
                    .onErrorResume(e -> {
                        log.error("查询交易记录失败", e);
                        return Mono.just(ResponseEntity.internalServerError().body(ApiResponse.error("查询交易记录失败")));
                    });
        } else if (status != null) {
            return txRepo.findByStatusOrderByCreatedAtDesc(status, pageable)
                    .collectList()
                    .map(list -> ResponseEntity.ok(ApiResponse.success(list)))
                    .onErrorResume(e -> {
                        log.error("查询交易记录失败", e);
                        return Mono.just(ResponseEntity.internalServerError().body(ApiResponse.error("查询交易记录失败")));
                    });
        } else if (userId != null) {
            return txRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                    .collectList()
                    .map(list -> ResponseEntity.ok(ApiResponse.success(list)))
                    .onErrorResume(e -> {
                        log.error("查询交易记录失败", e);
                        return Mono.just(ResponseEntity.internalServerError().body(ApiResponse.error("查询交易记录失败")));
                    });
        }
        return txRepo.findAllByOrderByCreatedAtDesc(pageable)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list)))
                .onErrorResume(e -> {
                    log.error("查询交易记录失败", e);
                    return Mono.just(ResponseEntity.internalServerError().body(ApiResponse.error("查询交易记录失败")));
                });
    }

    @GetMapping(value = "/transactions/count", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<Long>>> countTransactions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userId) {
        Mono<Long> countMono;
        if (status != null && userId != null) {
            countMono = txRepo.countByUserIdAndStatus(userId, status);
        } else if (status != null) {
            countMono = txRepo.countByStatus(status);
        } else if (userId != null) {
            countMono = txRepo.countByUserId(userId);
        } else {
            countMono = txRepo.count();
        }
        return countMono
                .map(count -> ResponseEntity.ok(ApiResponse.success(count)))
                .onErrorResume(e -> {
                    log.error("统计交易记录数量失败", e);
                    return Mono.just(ResponseEntity.internalServerError().body(ApiResponse.error("统计交易记录数量失败")));
                });
    }

    @GetMapping(value = "/transactions/{traceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<CreditTransaction>>> getTransaction(@PathVariable String traceId) {
        return txRepo.findByTraceId(traceId)
                .map(tx -> ResponseEntity.ok(ApiResponse.success(tx)))
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .onErrorResume(e -> {
                    log.error("查询交易记录失败: traceId={}", traceId, e);
                    return Mono.just(ResponseEntity.internalServerError().body(ApiResponse.error("查询交易记录失败")));
                });
    }

    @PostMapping(value = "/transactions/{traceId}/reverse", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<CreditTransaction>>> reverse(
            @PathVariable String traceId,
            @Valid @RequestBody ReverseRequest req) {
        if (req.getOperatorUserId() == null || req.getOperatorUserId().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(ApiResponse.error("操作员用户ID不能为空")));
        }
        if (req.getReason() == null || req.getReason().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(ApiResponse.error("冲正原因不能为空")));
        }
        return reversalService.reverseByTraceId(traceId, req.getOperatorUserId(), req.getReason())
                .map(tx -> ResponseEntity.ok(ApiResponse.success(tx)))
                .onErrorResume(e -> {
                    log.error("冲正交易失败: traceId={}", traceId, e);
                    return Mono.just(ResponseEntity.badRequest().body(ApiResponse.error("冲正交易失败: " + e.getMessage())));
                });
    }

    @Data
    public static class ReverseRequest {
        @NotBlank(message = "操作员用户ID不能为空")
        private String operatorUserId;

        @NotBlank(message = "冲正原因不能为空")
        private String reason;
    }
}
