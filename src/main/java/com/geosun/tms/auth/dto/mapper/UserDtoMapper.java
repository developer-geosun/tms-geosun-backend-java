package com.geosun.tms.auth.dto.mapper;

import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.response.RegisterResponse;
import com.geosun.tms.auth.dto.response.UserPublicDto;

/**
 * Маппінг сутності {@link User} у DTO для API (без passwordHash).
 */
public final class UserDtoMapper {

    private UserDtoMapper() {
    }

    public static UserPublicDto toPublicDto(User user) {
        if (user == null) {
            return null;
        }
        return new UserPublicDto(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    public static RegisterResponse toRegisterResponse(User user) {
        if (user == null) {
            return null;
        }
        return new RegisterResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
