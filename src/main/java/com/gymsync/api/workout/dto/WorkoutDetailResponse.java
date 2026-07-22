package com.gymsync.api.workout.dto;

import com.gymsync.api.workout.domain.ImportStatus;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.domain.WorkoutSource;
import com.gymsync.api.workout.domain.WorkoutStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Detail representation for a single workout: metadata plus its sets and exercise info. */
public record WorkoutDetailResponse(
    UUID id,
    Instant startedAt,
    Instant endedAt,
    WorkoutStatus status,
    WorkoutSource source,
    ImportStatus importStatus,
    Instant createdAt,
    Instant updatedAt,
    List<WorkoutSetResponse> sets) {

  public static WorkoutDetailResponse from(Workout workout, List<WorkoutSetResponse> sets) {
    return new WorkoutDetailResponse(
        workout.getId(),
        workout.getStartedAt(),
        workout.getEndedAt(),
        workout.getStatus(),
        workout.getSource(),
        workout.getImportStatus(),
        workout.getCreatedAt(),
        workout.getUpdatedAt(),
        sets);
  }
}
