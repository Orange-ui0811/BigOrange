package com.orangejd.server.config;

import com.orangejd.common.properties.JwtProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;

/** Fails production startup before serving traffic when a security-critical setting is unsafe. */
@Component
@Profile("prod")
public class ProductionConfigurationValidator implements InitializingBean {
    private final JwtProperties jwt;
    private final EpayProperties epay;

    @Value("${orange.security.allowed-origins:}") private String allowedOrigins;
    @Value("${orange.turnstile.secret-key:}") private String turnstileSecret;

    public ProductionConfigurationValidator(JwtProperties jwt, EpayProperties epay) {
        this.jwt = jwt;
        this.epay = epay;
    }

    @Override
    public void afterPropertiesSet() {
        requireStrongSecret("JWT_ADMIN_SECRET", jwt.getAdminSecretKey());
        requireStrongSecret("JWT_APP_SECRET", jwt.getAppSecretKey());
        if (jwt.getAdminSecretKey().equals(jwt.getAppSecretKey())) {
            throw new IllegalStateException("管理员与用户 JWT 密钥必须不同");
        }
        if (!hasText(allowedOrigins) || allowedOrigins.contains("*")) {
            throw new IllegalStateException("ALLOWED_ORIGINS 必须是明确的 HTTPS 域名白名单");
        }
        for (String origin : allowedOrigins.split(",")) {
            requireHttpsUrl("生产 CORS 来源", origin.trim(), true);
        }
        if (!hasText(turnstileSecret)) {
            throw new IllegalStateException("生产环境必须配置 TURNSTILE_SECRET_KEY");
        }
        boolean anyPaymentValue = hasText(epay.getPid()) || hasText(epay.getKey()) || hasText(epay.getGateway())
                || hasText(epay.getNotifyUrl()) || hasText(epay.getReturnUrl());
        if (anyPaymentValue && !epay.isConfigured()) {
            throw new IllegalStateException("支付配置不完整；请全部配置或全部留空以关闭支付");
        }
        if (epay.isConfigured()) {
            requireHttpsUrl("EPAY_GATEWAY", epay.getGateway(), false);
            requireHttpsUrl("EPAY_NOTIFY_URL", epay.getNotifyUrl(), false);
            requireHttpsUrl("EPAY_RETURN_URL", epay.getReturnUrl(), false);
            if (epay.getKey().length() < 16) {
                throw new IllegalStateException("EPAY_KEY 长度不足，生产环境至少需要 16 字符");
            }
        }
    }

    private void requireStrongSecret(String name, String value) {
        if (!hasText(value) || value.length() < 32 || value.startsWith("change-me")) {
            throw new IllegalStateException(name + " 必须是至少 32 字符的随机密钥");
        }
    }

    private void requireHttpsUrl(String name, String value, boolean originOnly) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !hasText(uri.getHost())
                    || uri.getUserInfo() != null || uri.getFragment() != null
                    || (originOnly && (uri.getQuery() != null
                    || (hasText(uri.getPath()) && !"/".equals(uri.getPath()))))) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException(name + " 必须是合法的 HTTPS 地址");
        }
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
