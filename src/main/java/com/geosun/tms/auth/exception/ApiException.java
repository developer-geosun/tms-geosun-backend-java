package com.geosun.tms.auth.exception;

/**
 * Бізнес/HTTP помилка API зі стабільним {@code code} для клієнта.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(400, code, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(401, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(403, code, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, "CONFLICT", message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(429, "RATE_LIMIT_EXCEEDED", message);
    }

    public static ApiException serviceUnavailable(String code, String message) {
        return new ApiException(503, code, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message);
    }
}
