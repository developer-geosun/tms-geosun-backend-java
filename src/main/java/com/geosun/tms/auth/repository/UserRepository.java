package com.geosun.tms.auth.repository;

import com.geosun.tms.auth.domain.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

  Optional<User> findByEmailAndDeletedFalse(String email);

  /**
   * Для login: спочатку активний запис з email, інакше видалений (щоб повернути 403 USER_DELETED).
   */
  Optional<User> findTopByEmailOrderByDeletedAsc(String email);

  boolean existsByEmailAndDeletedFalse(String email);
}
