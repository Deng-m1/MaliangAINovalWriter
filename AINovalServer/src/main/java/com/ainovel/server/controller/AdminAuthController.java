package com.ainovel.server.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ainovel.server.common.response.ApiResponse;
import com.ainovel.server.domain.model.User;
import com.ainovel.server.service.JwtService;
import com.ainovel.server.service.RoleService;
import com.ainovel.server.service.UserService;
import com.ainovel.server.web.dto.AdminAuthRequest;
import com.ainovel.server.web.dto.AdminAuthResponse;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

/**
 * 管理员认证控制器
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {
    
    private final UserService userService;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    
    @Autowired
    public AdminAuthController(UserService userService, RoleService roleService, 
                              PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userService = userService;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }
    
    /**
     * 管理员登录
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<ApiResponse<AdminAuthResponse>>> login(@Valid @RequestBody AdminAuthRequest request) {
        return userService.findUserByUsername(request.getUsername())
                .filter(user -> passwordEncoder.matches(request.getPassword(), user.getPassword()))
                .filter(user -> hasAdminRole(user))
                .flatMap(user -> {
                    // 获取用户的所有权限 - 使用用户的角色ID列表
                    return roleService.getUserPermissions(user.getRoleIds())
                            .map(permissions -> {
                                // 生成包含角色和权限的JWT令牌
                                String token = jwtService.generateTokenWithRolesAndPermissions(
                                    user, user.getRoles(), permissions);
                                String refreshToken = jwtService.generateRefreshToken(user);
                                
                                AdminAuthResponse response = new AdminAuthResponse(
                                        token,
                                        refreshToken,
                                        user.getId(),
                                        user.getUsername(),
                                        user.getDisplayName(),
                                        user.getRoles(),
                                        permissions
                                );
                                
                                return ResponseEntity.ok(ApiResponse.success(response));
                            });
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("用户名或密码错误，或无管理员权限")));
    }
    
    /**
     * 检查用户是否有管理员角色
     */
    private boolean hasAdminRole(User user) {
        if (user.getRoles() == null) {
            return false;
        }
        
        // 检查是否有管理员相关角色
        return user.getRoles().stream()
                .anyMatch(role -> role.toLowerCase().contains("admin") || 
                                role.toLowerCase().contains("super"));
    }
} 