package com.gymsync.api.workout.dto;

import com.gymsync.api.workout.domain.ImportStatus;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.domain.WorkoutSource;
import com.gymsync.api.workout.domain.WorkoutStatus;
import java.time.Instant;
import java.util.UUID;

/** Summary representation used for workout list views (no sets). */
public record WorkoutResponse(
    UUID id,
    Instant startedAt,
    Instant endedAt,
    WorkoutStatus status,
    WorkoutSource source,
    ImportStatus importStatus,
    Instant createdAt,
    Instant updatedAt) {

  public static WorkoutResponse from(Workout workout) {
    return new WorkoutResponse(
        workout.getId(),
        workout.getStartedAt(),
        workout.getEndedAt(),
        workout.getStatus(),
        workout.getSource(),
        workout.getImportStatus(),
        workout.getCreatedAt(),
        workout.getUpdatedAt());
  }
}
