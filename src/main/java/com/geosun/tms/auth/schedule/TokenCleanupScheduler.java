package com.geosun.tms.auth.schedule;

import com.geosun.tms.auth.service.TokenCleanupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Періодичний запуск очищення токенів за cron з конфігурації.
 */
@Component
public class TokenCleanupScheduler {

  private final TokenCleanupService tokenCleanupService;

  public TokenCleanupScheduler(TokenCleanupService tokenCleanupService) {
    this.tokenCleanupService = tokenCleanupService;
  }

  @Scheduled(cron = "${app.cleanup.cron}")
  public void runScheduledCleanup() {
    tokenCleanupService.purgeOldTokenRows();
  }
}
