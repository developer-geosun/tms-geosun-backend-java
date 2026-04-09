package com.geosun.tms.auth.security.jwt;

import java.time.Instant;

/**
 * Валідовані claims access JWT (sub + sessionId + час).
 */
public record JwtAccessClaims(String subjectUserId, String sessionId, Instant issuedAt, Instant expiresAt) {
}
