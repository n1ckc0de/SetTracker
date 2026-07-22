package com.gymsync.api.workout.repository;

import com.gymsync.api.user.domain.User;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.domain.WorkoutStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@code JpaSpecificationExecutor} backs the multi-filter {@code listWorkouts} query in {@code
 * WorkoutService}: filters (status/source/from/to) are only present in the query at all when the
 * caller actually supplies them, which sidesteps the Postgres "could not determine data type of
 * parameter" errors that a single static JPQL query with {@code :param IS NULL OR ...} placeholders
 * ran into for unused filters (see docs/DECISIONS.md).
 */
public interface WorkoutRepository
    extends JpaRepository<Workout, UUID>, JpaSpecificationExecutor<Workout> {

  Optional<Workout> findByIdAndUserId(UUID id, UUID userId);

  Optional<Workout> findByUserAndStatus(User user, WorkoutStatus status);

  /**
   * Counts workouts that count toward official statistics: completed, and either not an import
   * ({@code NOT_APPLICABLE}) or an import that has been explicitly {@code CONFIRMED}.
   * Pending-review and rejected imports never count (spec section 15/22).
   */
  @Query(
      """
      SELECT COUNT(w) FROM Workout w
      WHERE w.user = :user
        AND w.status = com.gymsync.api.workout.domain.WorkoutStatus.COMPLETED
        AND (w.importStatus = com.gymsync.api.workout.domain.ImportStatus.NOT_APPLICABLE
             OR w.importStatus = com.gymsync.api.workout.domain.ImportStatus.CONFIRMED)
        AND w.startedAt >= :from AND w.startedAt < :to
      """)
  long countCountableWorkouts(
      @Param("user") User user, @Param("from") Instant from, @Param("to") Instant to);
}
