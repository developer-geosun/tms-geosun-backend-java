package com.geosun.tms.auth.service;

import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.repository.RefreshTokenRepository;
import com.geosun.tms.auth.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * М'яке видалення користувача (ADMIN).
 */
@Service
public class UserDeletionService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;

  public UserDeletionService(
      UserRepository userRepository, RefreshTokenRepository refreshTokenRepository) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
  }

  @Transactional
  public void softDelete(String rawId) {
    try {
      UUID.fromString(rawId);
    } catch (IllegalArgumentException ex) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Invalid user id");
    }

    User user =
        userRepository.findById(rawId).orElseThrow(() -> ApiException.notFound("User not found"));

    if (user.isDeleted()) {
      return;
    }

    user.setDeleted(true);
    user.setDeletedAt(Instant.now());
    user.setActive(false);
    refreshTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
    userRepository.save(user);
  }
}
