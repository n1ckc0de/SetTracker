package com.gymsync.api.workout.dto;

import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.exercise.domain.ExerciseCategory;
import com.gymsync.api.workout.domain.SetType;
import com.gymsync.api.workout.domain.WeightEntryMode;
import com.gymsync.api.workout.domain.WorkoutSet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Includes the exercise info needed to display a set without a separate lookup. */
public record WorkoutSetResponse(
    UUID id,
    UUID exerciseId,
    String exerciseName,
    ExerciseCategory exerciseCategory,
    BigDecimal weight,
    Integer reps,
    Integer setNumber,
    Instant loggedAt,
    SetType setType,
    Integer rir,
    WeightEntryMode weightEntryMode,
    BigDecimal barWeight,
    BigDecimal enteredWeight,
    String note,
    boolean completed,
    Instant createdAt,
    Instant updatedAt,
    PrDetectionResult prEvent) {

  public static WorkoutSetResponse from(WorkoutSet set) {
    return from(set, null);
  }

  public static WorkoutSetResponse from(WorkoutSet set, PrDetectionResult prEvent) {
    Exercise exercise = set.getExercise();
    return new WorkoutSetResponse(
        set.getId(),
        exercise.getId(),
        exercise.getName(),
        exercise.getCategory(),
        set.getWeight(),
        set.getReps(),
        set.getSetNumber(),
        set.getLoggedAt(),
        set.getSetType(),
        set.getRir(),
        set.getWeightEntryMode(),
        set.getBarWeight(),
        set.getEnteredWeight(),
        set.getNote(),
        set.isCompleted(),
        set.getCreatedAt(),
        set.getUpdatedAt(),
        prEvent);
  }

  public WorkoutSetResponse withoutPrEvent() {
    return new WorkoutSetResponse(
        id,
        exerciseId,
        exerciseName,
        exerciseCategory,
        weight,
        reps,
        setNumber,
        loggedAt,
        setType,
        rir,
        weightEntryMode,
        barWeight,
        enteredWeight,
        note,
        completed,
        createdAt,
        updatedAt,
        null);
  }
}
