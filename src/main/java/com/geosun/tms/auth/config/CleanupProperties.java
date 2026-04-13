package com.geosun.tms.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Розклад і період зберігання метаданих токенів перед фізичним видаленням (app.cleanup.*).
 */
@ConfigurationProperties(prefix = "app.cleanup")
public class CleanupProperties {

  /**
   * Скільки днів зберігати рядки після expiry/revoke/used згідно ТЗ.
   */
  private int tokenRetentionDays = 30;

  /**
   * Cron для щоденного запуску (за замовчуванням 02:00 UTC).
   */
  private String cron = "0 0 2 * * *";

  public int getTokenRetentionDays() {
    return tokenRetentionDays;
  }

  public void setTokenRetentionDays(int tokenRetentionDays) {
    this.tokenRetentionDays = tokenRetentionDays;
  }

  public String getCron() {
    return cron;
  }

  public void setCron(String cron) {
    this.cron = cron;
  }
}
