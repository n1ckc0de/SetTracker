package com.gymsync.api.stats.repository;

import com.gymsync.api.workout.domain.WorkoutSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read-only aggregation queries backing weekly statistics, personal records, and progressive
 * overload recommendations. Every query filters to "countable" sets: sets that belong to a {@code
 * COMPLETED} workout that is either not an import ({@code NOT_APPLICABLE}) or an import that has
 * been explicitly {@code CONFIRMED} — pending-review and rejected imports are excluded (spec
 * sections 14/15/22).
 *
 * <p>Uses {@code FROM WorkoutSet ws JOIN FETCH ws.workout w} rather than a {@code LEFT JOIN} on
 * {@code Workout}: every {@code WorkoutSet} has a mandatory, non-null {@code workout} association,
 * so an inner join (with the associated workout eagerly fetched in the same query to avoid N+1
 * lookups) is both correct and cheaper than an outer join.
 */
public interface StatsQueryRepository extends JpaRepository<WorkoutSet, UUID> {

  @Query(
      """
      SELECT ws FROM WorkoutSet ws
      JOIN FETCH ws.workout w
      JOIN FETCH ws.exercise e
      WHERE w.user.id = :userId
        AND w.status = com.gymsync.api.workout.domain.WorkoutStatus.COMPLETED
        AND (w.importStatus = com.gymsync.api.workout.domain.ImportStatus.NOT_APPLICABLE
             OR w.importStatus = com.gymsync.api.workout.domain.ImportStatus.CONFIRMED)
        AND w.startedAt >= :from AND w.startedAt < :to
      """)
  List<WorkoutSet> findCountableSetsBetween(
      @Param("userId") UUID userId, @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      """
      SELECT ws FROM WorkoutSet ws
      JOIN FETCH ws.workout w
      JOIN FETCH ws.exercise e
      WHERE w.user.id = :userId
        AND e.id = :exerciseId
        AND w.status = com.gymsync.api.workout.domain.WorkoutStatus.COMPLETED
        AND (w.importStatus = com.gymsync.api.workout.domain.ImportStatus.NOT_APPLICABLE
             OR w.importStatus = com.gymsync.api.workout.domain.ImportStatus.CONFIRMED)
      ORDER BY w.startedAt DESC, ws.weight DESC, ws.reps DESC
      """)
  List<WorkoutSet> findCountableSetsForExercise(
      @Param("userId") UUID userId, @Param("exerciseId") UUID exerciseId);
}
