package com.geosun.tms.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Тіло {@code POST /api/v1/auth/verify-email}.
 */
public record VerifyEmailRequest(@NotBlank String token) {}
