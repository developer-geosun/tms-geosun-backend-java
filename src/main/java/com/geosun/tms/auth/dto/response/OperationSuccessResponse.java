package com.geosun.tms.auth.dto.response;

/**
 * Уніфікована відповідь для verify-email та resend-verification (поля success + message).
 */
public record OperationSuccessResponse(boolean success, String message) {}
