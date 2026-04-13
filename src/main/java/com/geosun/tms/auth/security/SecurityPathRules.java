package com.geosun.tms.auth.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Маршрути без access JWT (публічні) згідно ТЗ та actuator/swagger.
 */
public final class SecurityPathRules {

  private SecurityPathRules() {}

  public static boolean isPublic(HttpServletRequest request) {
    String path = normalizePath(request);
    String method = request.getMethod();
    if ("GET".equalsIgnoreCase(method)) {
      if ("/actuator/health".equals(path) || path.startsWith("/actuator/health/")) {
        return true;
      }
      if ("/swagger-ui.html".equals(path)
          || path.startsWith("/swagger-ui/")
          || path.startsWith("/v3/api-docs")) {
        return true;
      }
    }
    if ("POST".equalsIgnoreCase(method)) {
      return switch (path) {
        case "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/refresh" ->
            true;
        default -> false;
      };
    }
    return false;
  }

  private static String normalizePath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String context = request.getContextPath();
    if (context != null && !context.isEmpty() && uri.startsWith(context)) {
      uri = uri.substring(context.length());
    }
    if (uri.isEmpty()) {
      return "/";
    }
    return uri;
  }
}
