package com.geosun.tms.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ліміти з ТЗ (app.rate-limit.*).
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private int loginMaxAttempts = 5;

    private int loginWindowSeconds = 900;

    private int resendVerificationSeconds = 60;

    private int refreshMaxRequests = 30;

    private int refreshWindowSeconds = 60;

    /** Максимум реєстрацій з одного IP за вікно (додатково до ТЗ, щоб закрити 429 у матриці). */
    private int registerMaxPerWindow = 30;

    public int getLoginMaxAttempts() {
        return loginMaxAttempts;
    }

    public void setLoginMaxAttempts(int loginMaxAttempts) {
        this.loginMaxAttempts = loginMaxAttempts;
    }

    public int getLoginWindowSeconds() {
        return loginWindowSeconds;
    }

    public void setLoginWindowSeconds(int loginWindowSeconds) {
        this.loginWindowSeconds = loginWindowSeconds;
    }

    public int getResendVerificationSeconds() {
        return resendVerificationSeconds;
    }

    public void setResendVerificationSeconds(int resendVerificationSeconds) {
        this.resendVerificationSeconds = resendVerificationSeconds;
    }

    public int getRefreshMaxRequests() {
        return refreshMaxRequests;
    }

    public void setRefreshMaxRequests(int refreshMaxRequests) {
        this.refreshMaxRequests = refreshMaxRequests;
    }

    public int getRefreshWindowSeconds() {
        return refreshWindowSeconds;
    }

    public void setRefreshWindowSeconds(int refreshWindowSeconds) {
        this.refreshWindowSeconds = refreshWindowSeconds;
    }

    public int getRegisterMaxPerWindow() {
        return registerMaxPerWindow;
    }

    public void setRegisterMaxPerWindow(int registerMaxPerWindow) {
        this.registerMaxPerWindow = registerMaxPerWindow;
    }
}
