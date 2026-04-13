package com.geosun.tms.auth.repository;

import com.geosun.tms.auth.domain.token.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationToken, String> {

  Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

  @Modifying
  @Query("delete from EmailVerificationToken e where e.user.id = :userId and e.usedAt is null")
  void deletePendingByUserId(@Param("userId") String userId);

  /**
   * Фізичне видалення: прострочені без used_at або використані старші за cutoff (ТЗ, п. 8 БД).
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      delete from EmailVerificationToken e
      where (e.usedAt is null and e.expiresAt < :cutoff)
         or (e.usedAt is not null and e.usedAt < :cutoff)
      """)
  int deleteExpiredOrUsedBefore(@Param("cutoff") Instant cutoff);
}
