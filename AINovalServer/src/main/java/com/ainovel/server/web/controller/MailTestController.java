package com.ainovel.server.web.controller;

import com.ainovel.server.service.MailTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 邮件测试控制器
 * Restricted to dev and test profiles only to prevent abuse in production.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Tag(name = "邮件测试", description = "邮件功能测试相关接口")
@org.springframework.context.annotation.Profile({"dev", "test", "open-dev"})
public class MailTestController {
    
    private final MailTestService mailTestService;
    
    /**
     * 测试邮件配置
     * @return 测试结果
     */
    @PostMapping("/mail/config")
    @Operation(summary = "测试邮件配置", description = "测试当前邮件服务器配置是否正确")
    public Mono<ResponseEntity<Map<String, Object>>> testMailConfig() {
        return mailTestService.testMailConnection()
                .map(result -> {
                    Map<String, Object> response = Map.of(
                            "success", result.success(),
                            "message", result.message(),
                            "details", result.details()
                    );
                    return ResponseEntity.ok(response);
                })
                .onErrorReturn(ResponseEntity.ok(Map.of(
                        "success", (Object) false,
                        "message", (Object) "邮件配置测试失败",
                        "details", (Object) "服务器内部错误"
                )));
    }
    
    /**
     * 发送测试邮件
     * @param testEmail 测试邮箱地址
     * @return 发送结果
     */
    @PostMapping("/mail/send")
    @Operation(summary = "发送测试邮件", description = "向指定邮箱发送测试邮件")
    public Mono<ResponseEntity<Map<String, Object>>> sendTestMail(
            @RequestParam @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String testEmail) {
        
        return mailTestService.sendTestMail(testEmail)
                .map(result -> {
                    Map<String, Object> response = Map.of(
                            "success", result.success(),
                            "message", result.message(),
                            "recipient", testEmail,
                            "timestamp", System.currentTimeMillis()
                    );
                    return ResponseEntity.ok(response);
                })
                .onErrorReturn(ResponseEntity.ok(Map.of(
                        "success", (Object) false,
                        "message", (Object) "测试邮件发送失败",
                        "recipient", (Object) testEmail
                )));
    }
    
    /**
     * 发送测试验证码
     * @param testEmail 测试邮箱地址
     * @return 发送结果
     */
    @PostMapping("/mail/verification-code")
    @Operation(summary = "发送测试验证码", description = "向指定邮箱发送测试验证码")
    public Mono<ResponseEntity<Map<String, Object>>> sendTestVerificationCode(
            @RequestParam @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String testEmail) {
        
        return mailTestService.sendTestVerificationCode(testEmail)
                .map(result -> {
                    Map<String, Object> response = Map.of(
                            "success", result.success(),
                            "message", result.message(),
                            "recipient", testEmail,
                            "code", result.verificationCode() != null ? result.verificationCode() : "",
                            "timestamp", System.currentTimeMillis()
                    );
                    return ResponseEntity.ok(response);
                })
                .onErrorReturn(ResponseEntity.ok(Map.of(
                        "success", (Object) false,
                        "message", (Object) "测试验证码发送失败",
                        "recipient", (Object) testEmail
                )));
    }
    
    /**
     * 获取邮件服务状态
     * @return 邮件服务状态
     */
    @GetMapping("/mail/status")
    @Operation(summary = "获取邮件服务状态", description = "获取当前邮件服务的配置和状态信息")
    public Mono<ResponseEntity<Map<String, Object>>> getMailStatus() {
        return mailTestService.getMailStatus()
                .map(status -> ResponseEntity.ok(Map.of(
                        "configured", status.configured(),
                        "host", status.host(),
                        "port", status.port(),
                        "username", status.username(),
                        "protocol", status.protocol(),
                        "lastTestTime", status.lastTestTime(),
                        "lastTestResult", status.lastTestResult()
                )));
    }
}