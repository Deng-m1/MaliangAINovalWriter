package com.ainovel.server.web.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 管理员面板入口控制器
 * 处理 /admin 和 /admin/ 请求，返回管理员面板的 index.html
 */
@RestController
public class AdminPanelController {

    @GetMapping(value = {"/admin", "/admin/"}, produces = MediaType.TEXT_HTML_VALUE)
    public Mono<ResponseEntity<Resource>> getAdminIndex() {
        Resource resource = new FileSystemResource("/app/admin_web/index.html");
        
        if (!resource.exists() || !resource.isReadable()) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }
        
        return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(resource));
    }
}
