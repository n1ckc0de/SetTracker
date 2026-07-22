package com.gymsync.api.coach.dto;

import com.gymsync.api.workout.domain.WorkoutSet;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CoachApplyPreviewResponse(UUID workoutId, UUID exerciseId, List<SetPreview> sets) {

  public record SetPreview(
      UUID setId,
      int setNumber,
      boolean completed,
      boolean willChange,
      BigDecimal currentWeight,
      Integer currentReps,
      Integer currentRir,
      BigDecimal proposedWeight,
      Integer proposedReps,
      Integer proposedRir) {

    public static SetPreview unchanged(WorkoutSet set) {
      return new SetPreview(
          set.getId(),
          set.getSetNumber(),
          true,
          false,
          set.getWeight(),
          set.getReps(),
          set.getRir(),
          null,
          null,
          null);
    }

    public static SetPreview proposed(
        WorkoutSet set, BigDecimal proposedWeight, int proposedReps, int proposedRir) {
      return new SetPreview(
          set.getId(),
          set.getSetNumber(),
          false,
          true,
          set.getWeight(),
          set.getReps(),
          set.getRir(),
          proposedWeight,
          proposedReps,
          proposedRir);
    }
  }
}
