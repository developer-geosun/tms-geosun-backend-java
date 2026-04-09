package com.geosun.tms.auth.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметри access/refresh JWT з application.yml (app.security.jwt).
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    /**
     * Секрет HMAC-SHA256 (мінімум 32 байти для HS256).
     */
    private String secret = "";

    private String issuer = "";

    private String audience = "";

    /**
     * TTL access token, секунди (ТЗ: 900).
     */
    private long expiresSeconds = 900;

    /**
     * TTL refresh token, секунди (ТЗ: 604800).
     */
    private long refreshExpiresSeconds = 604800;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public long getExpiresSeconds() {
        return expiresSeconds;
    }

    public void setExpiresSeconds(long expiresSeconds) {
        this.expiresSeconds = expiresSeconds;
    }

    public long getRefreshExpiresSeconds() {
        return refreshExpiresSeconds;
    }

    public void setRefreshExpiresSeconds(long refreshExpiresSeconds) {
        this.refreshExpiresSeconds = refreshExpiresSeconds;
    }
}
