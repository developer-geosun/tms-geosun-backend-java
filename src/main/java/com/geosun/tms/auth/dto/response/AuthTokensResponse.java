package com.geosun.tms.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Відповідь login / refresh: пара токенів та короткий профіль.
 */
public record AuthTokensResponse(
        @JsonProperty("accessToken") String accessToken,
        @JsonProperty("refreshToken") String refreshToken,
        @JsonProperty("tokenType") String tokenType,
        @JsonProperty("expiresIn") long expiresInSeconds,
        UserPublicDto user
) {
}
