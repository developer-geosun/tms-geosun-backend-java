package com.geosun.tms.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Публічні поля користувача для login / me / register (без чутливих даних).
 */
public record UserPublicDto(String id, String email, @JsonProperty("role") String roleName) {}
