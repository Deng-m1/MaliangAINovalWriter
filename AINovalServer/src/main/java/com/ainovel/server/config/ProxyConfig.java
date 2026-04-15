package com.ainovel.server.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;

import com.ainovel.server.service.ai.AIModelProvider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 代理配置类
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyConfig {

    /**
     * 是否启用代理
     */
    @Value("${proxy.enabled:false}")
    private boolean enabled;

    /**
     * 代理主机
     */
    @Value("${proxy.host:localhost}")
    private String host;

    /**
     * 代理端口
     */
    @Value("${proxy.port:6888}")
    private int port;

    /**
     * 代理用户名（如需认证）
     */
    @Value("${proxy.username:}")
    private String username;

    /**
     * 代理密码（如需认证）
     */
    @Value("${proxy.password:}")
    private String password;

    /**
     * 是否通过 System.setProperty 应用 http/https 代理属性（仅影响当前JVM）
     */
    @Value("${proxy.applySystemProperties:true}")
    private boolean applySystemProperties;

    /**
     * 是否设置全局 ProxySelector（Java 11+ HttpClient 使用）。默认关闭以避免全局副作用。
     */
    @Value("${proxy.applyProxySelector:false}")
    private boolean applyProxySelector;

    /**
     * 代理类型：http 或 socks
     */
    @Value("${proxy.type:http}")
    private String type;

    /**
     * 是否信任所有证书（仅限排障时临时开启，生产默认为 false）
     */
    @Value("${proxy.trustAllCerts:false}")
    private boolean trustAllCerts;

    /**
     * 获取完整的代理地址
     * 
     * @return 代理地址，格式为 host:port
     */
    public String getProxyAddress() {
        return host + ":" + port;
    }

    /**
     * 检查代理配置是否有效
     * 
     * @return 是否有效
     */
    public boolean isValid() {
        return enabled && host != null && !host.isEmpty() && port > 0;
    }

    /**
     * 对多个AI模型提供商应用代理配置
     * 
     * @param providers AI模型提供商列表
     */
    public void applyToProviders(List<AIModelProvider> providers) {
        if (enabled && isValid()) {
            log.info("正在为AI模型提供商配置HTTP代理: {}:{}", host, port);
            
            for (AIModelProvider provider : providers) {
                try {
                    provider.setProxy(host, port);
                    provider.setTrustAllCerts(trustAllCerts);
                    log.info("已为 {} 模型提供商配置代理", provider.getProviderName());
                } catch (Exception e) {
                    log.error("为 {} 模型提供商配置代理时出错: {}", 
                            provider.getProviderName(), e.getMessage(), e);
                }
            }
        }
    }
} 