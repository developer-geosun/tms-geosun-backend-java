package com.geosun.tms.auth.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Єдиний формат помилки API (розділ 5.2 ТЗ).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
    String timestamp,
    int status,
    String error,
    String code,
    String message,
    String path,
    Map<String, String> details) {

  public static ApiErrorResponse of(
      String path, int status, String error, String code, String message) {
    return new ApiErrorResponse(Instant.now().toString(), status, error, code, message, path, null);
  }

  public static ApiErrorResponse withDetails(
      String path,
      int status,
      String error,
      String code,
      String message,
      Map<String, String> details) {
    return new ApiErrorResponse(
        Instant.now().toString(), status, error, code, message, path, details);
  }
}
