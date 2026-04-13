package com.geosun.tms.auth.ratelimit;

import com.geosun.tms.auth.config.RateLimitProperties;
import com.geosun.tms.auth.exception.ApiException;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Service;

/**
 * In-memory rate limiting для login (лише невдалі спроби), resend та refresh.
 */
@Service
public class RateLimitService {

  private final RateLimitProperties properties;
  private final ConcurrentHashMap<String, Deque<Long>> loginFailures = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Deque<Long>> refreshHits = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Deque<Long>> registerHits = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> resendLastMillis = new ConcurrentHashMap<>();

  public RateLimitService(RateLimitProperties properties) {
    this.properties = properties;
  }

  public void checkRegister(String clientIp) {
    String key = "register|" + clientIp;
    slidingWindowAllow(
        key,
        registerHits,
        properties.getRegisterMaxPerWindow(),
        properties.getLoginWindowSeconds());
  }

  public void checkLoginNotBlocked(String clientIp, String normalizedEmail) {
    String key = clientIp + "|" + normalizedEmail;
    Deque<Long> dq = loginFailures.get(key);
    if (dq == null) {
      return;
    }
    synchronized (dq) {
      prune(dq, properties.getLoginWindowSeconds());
      if (dq.size() >= properties.getLoginMaxAttempts()) {
        throw ApiException.tooManyRequests("Too many failed login attempts");
      }
    }
  }

  public void recordLoginFailure(String clientIp, String normalizedEmail) {
    String key = clientIp + "|" + normalizedEmail;
    Deque<Long> dq = loginFailures.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
    synchronized (dq) {
      prune(dq, properties.getLoginWindowSeconds());
      dq.addLast(System.currentTimeMillis());
    }
  }

  public void clearLoginFailures(String clientIp, String normalizedEmail) {
    loginFailures.remove(clientIp + "|" + normalizedEmail);
  }

  public void checkResend(String normalizedEmail) {
    long now = System.currentTimeMillis();
    long windowMs = properties.getResendVerificationSeconds() * 1000L;
    synchronized (resendLastMillis) {
      Long last = resendLastMillis.get(normalizedEmail);
      if (last != null && now - last < windowMs) {
        throw ApiException.tooManyRequests("Too many resend requests for this email");
      }
      resendLastMillis.put(normalizedEmail, now);
    }
  }

  public void checkRefresh(String clientIp) {
    String key = "refresh|" + clientIp;
    slidingWindowAllow(
        key, refreshHits, properties.getRefreshMaxRequests(), properties.getRefreshWindowSeconds());
  }

  private void slidingWindowAllow(
      String key, ConcurrentHashMap<String, Deque<Long>> map, int max, int windowSeconds) {
    Deque<Long> dq = map.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
    synchronized (dq) {
      prune(dq, windowSeconds);
      if (dq.size() >= max) {
        throw ApiException.tooManyRequests("Rate limit exceeded");
      }
      dq.addLast(System.currentTimeMillis());
    }
  }

  private void prune(Deque<Long> dq, int windowSeconds) {
    long now = System.currentTimeMillis();
    long windowMs = windowSeconds * 1000L;
    while (!dq.isEmpty() && now - dq.peekFirst() > windowMs) {
      dq.pollFirst();
    }
  }

  /**
   * Скидання лічильників для ізольованих інтеграційних тестів.
   */
  public void resetForTests() {
    loginFailures.clear();
    refreshHits.clear();
    registerHits.clear();
    synchronized (resendLastMillis) {
      resendLastMillis.clear();
    }
  }
}
