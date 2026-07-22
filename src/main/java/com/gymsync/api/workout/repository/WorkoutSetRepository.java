package com.gymsync.api.workout.repository;

import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.domain.WorkoutSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, UUID> {

  List<WorkoutSet> findByWorkoutOrderByLoggedAtAscSetNumberAsc(Workout workout);

  Optional<WorkoutSet> findByIdAndWorkoutId(UUID id, UUID workoutId);

  boolean existsByWorkoutAndExerciseAndSetNumber(
      Workout workout, Exercise exercise, Integer setNumber);

  boolean existsByWorkoutAndExerciseAndSetNumberAndIdNot(
      Workout workout, Exercise exercise, Integer setNumber, UUID id);

  /**
   * Loads a workout's sets with their exercise eagerly fetched in a single query, avoiding N+1
   * lookups when mapping to {@code WorkoutSetResponse} (open-in-view is disabled, so this must
   * happen inside the owning {@code @Transactional} service method).
   */
  @Query(
      """
      SELECT ws FROM WorkoutSet ws JOIN FETCH ws.exercise
      WHERE ws.workout = :workout
      ORDER BY ws.setNumber ASC, ws.loggedAt ASC
      """)
  List<WorkoutSet> findByWorkoutFetchExercise(@Param("workout") Workout workout);

  @Query(
      """
      SELECT ws FROM WorkoutSet ws
      JOIN FETCH ws.workout w
      JOIN FETCH w.user
      JOIN FETCH ws.exercise
      WHERE ws.id = :id
      """)
  Optional<WorkoutSet> findByIdFetchWorkoutAndExercise(@Param("id") UUID id);
}
