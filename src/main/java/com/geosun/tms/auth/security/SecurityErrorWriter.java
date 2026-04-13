package com.geosun.tms.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;

/**
 * JSON-помилки для фільтра та entry point (узгоджено з форматом ТЗ до глобального handler).
 */
public final class SecurityErrorWriter {

  private SecurityErrorWriter() {}

  public static void writeJson(
      HttpServletResponse response,
      ObjectMapper objectMapper,
      int status,
      String errorTitle,
      String code,
      String message,
      String path)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", status);
    body.put("error", errorTitle);
    body.put("code", code);
    body.put("message", message);
    body.put("path", path);

    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
