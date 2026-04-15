package com.ainovel.server.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import com.ainovel.server.security.JwtAuthenticationManager;
import com.ainovel.server.security.JwtServerAuthenticationConverter;

/**
 * 安全配置类 配置JWT认证和授权规则
 */
@Configuration
@EnableWebFluxSecurity
@Profile("!test")
public class SecurityConfig {

    private final ReactiveAuthenticationManager authenticationManager;
    private final ServerAuthenticationConverter authenticationConverter;

    @Autowired
    public SecurityConfig(JwtAuthenticationManager authenticationManager,
            JwtServerAuthenticationConverter authenticationConverter) {
        this.authenticationManager = authenticationManager;
        this.authenticationConverter = authenticationConverter;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        // 创建JWT认证过滤器
        AuthenticationWebFilter authenticationWebFilter = new AuthenticationWebFilter(authenticationManager);
        authenticationWebFilter.setServerAuthenticationConverter(authenticationConverter);
        // 只对需要认证的路径进行认证检查
        authenticationWebFilter.setRequiresAuthenticationMatcher(
                ServerWebExchangeMatchers.pathMatchers("/api/v1/novels/**", "/api/v1/scenes/**", 
                    "/api/v1/users/**", "/api/v1/ai/**", "/api/v1/chats/**", "/api/v1/user-ai-configs/**",
                    "/api/v1/ai-chat/**", "/api/v1/api/users/**", "/api/v1/api/tasks/**", 
                    "/api/v1/api/models/**", "/api/v1/security-test/**", "/api/v1/mongo-test/**",
                    "/api/v1/novel-snippets/**", "/api/v1/ai-chat-history/**", "/api/v1/api/user-editor-settings/**",
                    "/api/v1/prompts/**", "/api/v1/prompt-aggregation/**", "/api/v1/prompt-templates/**", "/api/v1/content-provider/**",
                    "/api/v1/admin/**","/api/v1/public-models/**","/api/v1/credits/**","/api/v1/preset-aggregation/**", "/api/v1/presets/**",
                    "/api/v1/setting-histories/**","/api/v1/setting-generation/**","/api/v1/test/setting-generation/**","/api/v1/compose/**","/api/v1/tool-orchestration/**",
                    "/api/v1/analytics/**", "/api/v1/payments/**", "/api/v1/pricing/**",
                    "/api/v1/knowledge-bases/**", "/api/v1/knowledge-extraction-tasks/**", "/api/v1/prompt-market/**",
                    "/api/v1/test/**", "/api/test/**")
        );
        
        // 添加认证失败处理器
        authenticationWebFilter.setAuthenticationFailureHandler(
            (exchange, ex) -> {
                exchange.getExchange().getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return exchange.getExchange().getResponse().setComplete();
            }
        );

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(corsSpec -> corsSpec.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                // 公开端点
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 静态资源和根路径
                .pathMatchers("/", "/index.html", "/favicon.ico", "/manifest.json").permitAll()
                .pathMatchers("/assets/**", "/icons/**", "/canvaskit/**", "/*.js", "/*.css").permitAll()
                // 管理员面板静态资源
                .pathMatchers("/admin", "/admin/", "/admin/index.html", "/admin/favicon.ico", "/admin/manifest.json").permitAll()
                .pathMatchers("/admin/assets/**", "/admin/icons/**", "/admin/canvaskit/**", "/admin/*.js", "/admin/*.css", "/admin/fonts/**").permitAll()
                // 放开 Actuator health/prometheus 端点给 Prometheus 抓取; block all other actuator endpoints
                .pathMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                .pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .pathMatchers("/actuator/**").authenticated()
                // Swagger/OpenAPI: require authentication in non-dev profiles
                .pathMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/webjars/**").authenticated()
                .pathMatchers("/api/v1/auth/**").permitAll()
                .pathMatchers("/api/v1/auth/login").permitAll()
                .pathMatchers("/api/v1/auth/login/phone").permitAll()
                .pathMatchers("/api/v1/auth/login/email").permitAll()
                .pathMatchers("/api/v1/auth/register").permitAll()
                .pathMatchers("/api/v1/auth/register/quick").permitAll()
                .pathMatchers("/api/v1/auth/verification-code").permitAll()
                .pathMatchers("/api/v1/auth/captcha").permitAll()
                .pathMatchers("/api/v1/admin/auth/**").permitAll() // 管理员登录端点
                .pathMatchers("/api/v1/users/register").permitAll()
                // 订阅与点数包：公开获取
                .pathMatchers("/api/v1/subscription-plans/**").permitAll()
                .pathMatchers("/api/v1/credit-packs/**").permitAll()
                // 设定生成：放开 GET /strategies 供游客拉取公共策略
                .pathMatchers(HttpMethod.GET, "/api/v1/setting-generation/strategies").permitAll()
                
                // AI提供商能力/测试/默认端点：放行
                .pathMatchers("/api/v1/providers/**").permitAll()
                // AI提供商能力/测试/默认端点：放行
                .pathMatchers("/api/v1/models/**").permitAll()
                // 模型定价查询：GET请求放行，让前端可以检查定价
                .pathMatchers(HttpMethod.GET, "/api/v1/pricing/**").permitAll()
                // 模型定价管理：POST/PUT/DELETE需要管理员认证
                .pathMatchers(HttpMethod.POST, "/api/v1/pricing/**").authenticated()
                .pathMatchers(HttpMethod.PUT, "/api/v1/pricing/**").authenticated()
                .pathMatchers(HttpMethod.DELETE, "/api/v1/pricing/**").authenticated()
                 // 需要认证的端点
                .pathMatchers("/api/v1/setting-generation/**").authenticated()
                .pathMatchers("/api/v1/test/setting-generation/**").authenticated()
                .pathMatchers("/api/v1/setting-histories/**").authenticated()
                .pathMatchers("/api/v1/novels/**").authenticated()
                .pathMatchers("/api/v1/preset-aggregation/**").authenticated()
                .pathMatchers("/api/v1/presets/**").authenticated()
                .pathMatchers("/api/v1/scenes/**").authenticated()
                .pathMatchers("/api/v1/users/**").authenticated()
                .pathMatchers("/api/v1/ai/**").authenticated()
                .pathMatchers("/api/v1/chats/**").authenticated()
                .pathMatchers("/api/v1/user-ai-configs/**").authenticated()
                .pathMatchers("/api/v1/ai-chat/**").authenticated()
                .pathMatchers("/api/v1/api/users/**").authenticated()
                .pathMatchers("/api/v1/api/tasks/**").authenticated()
                .pathMatchers("/api/v1/api/models/**").authenticated()
                .pathMatchers("/api/v1/security-test/**").authenticated()
                .pathMatchers("/api/v1/mongo-test/**").authenticated()
                .pathMatchers("/api/v1/novel-snippets/**").authenticated()
                .pathMatchers("/api/v1/ai-chat-history/**").authenticated()
                .pathMatchers("/api/v1/api/user-editor-settings/**").authenticated()
                .pathMatchers("/api/v1/public-models/**").authenticated()
                .pathMatchers("/api/v1/credits/**").authenticated()
                .pathMatchers("/api/v1/compose/**").authenticated()
                .pathMatchers("/api/v1/tool-orchestration/**").authenticated()
                // 数据分析接口：需要用户认证
                .pathMatchers("/api/v1/analytics/**").authenticated()
                // 新增的提示词相关API端点
                .pathMatchers("/api/v1/prompts/**").authenticated()
                .pathMatchers("/api/v1/prompt-aggregation/**").authenticated()
                .pathMatchers("/api/v1/prompt-templates/**").authenticated()
                .pathMatchers("/api/v1/content-provider/**").authenticated()
                // 管理员API端点
                .pathMatchers("/api/v1/admin/**").authenticated()
                // 支付：回调放行，其余需要认证
                .pathMatchers("/api/v1/payments/notify/**").permitAll()
                .pathMatchers("/api/v1/payments/**").authenticated()
                // 知识库：搜索和详情放行（允许游客查看公共知识库），其余需要认证
                .pathMatchers(HttpMethod.GET, "/api/v1/knowledge-bases/fanqie/search").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/v1/knowledge-bases/fanqie/*").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/v1/knowledge-bases/public").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/v1/knowledge-bases/*/detail").permitAll()
                .pathMatchers("/api/v1/knowledge-bases/**").authenticated()
                // AI拆书任务管理
                .pathMatchers("/api/v1/knowledge-extraction-tasks/**").authenticated()
                // 提示词市场
                .pathMatchers("/api/v1/prompt-market/**").authenticated()
                // Test endpoints: require authentication (also profile-restricted)
                .pathMatchers("/api/v1/test/**").authenticated()
                .pathMatchers("/api/test/**").authenticated()
                // 其他所有请求需要认证
                .anyExchange().authenticated()
                )
                // 显式设置全局认证管理器，避免默认过滤器无provider导致500
                .authenticationManager(authenticationManager)
                // 兜底：未认证时统一返回401
                .exceptionHandling(spec -> spec.authenticationEntryPoint((swe, e) -> {
                    swe.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                    return swe.getResponse().setComplete();
                }))
                // 使用addFilterAt替代addFilter，并指定正确的过滤器顺序
                .addFilterAt(authenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // 使用无状态会话
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Use allowedOriginPatterns instead of wildcard "*" to support credentials.
        // In production, restrict this to your actual frontend domain(s) via
        // the CORS_ALLOWED_ORIGINS environment variable (comma-separated).
        String allowedOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        } else {
            // Default: allow all origin patterns (development convenience).
            // For production, always set CORS_ALLOWED_ORIGINS.
            configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        }
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
