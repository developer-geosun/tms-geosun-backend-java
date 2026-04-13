package com.geosun.tms.auth.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Визначення IP клієнта з урахуванням X-Forwarded-For.
 */
@Component
public class ClientIpResolver {

  public String resolve(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(forwarded)) {
      String first = forwarded.split(",")[0].trim();
      if (StringUtils.hasText(first)) {
        return first;
      }
    }
    String ip = request.getRemoteAddr();
    return ip != null ? ip : "unknown";
  }
}
