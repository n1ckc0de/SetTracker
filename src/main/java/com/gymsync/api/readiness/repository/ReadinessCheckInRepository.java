package com.gymsync.api.readiness.repository;

import com.gymsync.api.readiness.domain.ReadinessCheckIn;
import com.gymsync.api.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReadinessCheckInRepository extends JpaRepository<ReadinessCheckIn, UUID> {

  Optional<ReadinessCheckIn> findFirstByUserOrderByCreatedAtDesc(User user);

  @Query(
      """
      SELECT r FROM ReadinessCheckIn r
      WHERE r.user = :user
        AND r.createdAt >= :from
        AND r.createdAt < :to
      ORDER BY r.createdAt DESC
      """)
  List<ReadinessCheckIn> findByUserAndCreatedAtBetween(
      @Param("user") User user, @Param("from") Instant from, @Param("to") Instant to);

  List<ReadinessCheckIn> findTop5ByUserOrderByCreatedAtDesc(User user);
}
