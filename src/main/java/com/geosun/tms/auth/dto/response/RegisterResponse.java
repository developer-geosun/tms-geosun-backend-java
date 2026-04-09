package com.geosun.tms.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Відповідь {@code 201} після реєстрації.
 */
public record RegisterResponse(
        String id,
        String email,
        @JsonProperty("role")
        String roleName
) {
}
