package com.geosun.tms.auth.repository;

import com.geosun.tms.auth.domain.token.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("select r from RefreshToken r join fetch r.user where r.id = :id")
    Optional<RefreshToken> findByIdWithUser(@Param("id") String id);

    @Query("select r from RefreshToken r join fetch r.user where r.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashWithUser(@Param("hash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken r set r.revokedAt = :ts where r.user.id = :userId and r.revokedAt is null")
    int revokeAllActiveByUserId(@Param("userId") String userId, @Param("ts") Instant ts);
}
