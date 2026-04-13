package com.geosun.tms.auth.dto.response;

/**
 * Успішний вихід з сесії.
 */
public record LogoutResponse(boolean success, String message) {}
