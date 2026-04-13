package com.geosun.tms.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Тіло {@code POST /api/v1/auth/refresh}.
 */
public record RefreshRequest(@NotBlank @JsonProperty("refreshToken") String refreshToken) {}
