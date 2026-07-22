package com.gymsync.api.workout.service;

import com.gymsync.api.common.exception.BadRequestException;
import com.gymsync.api.workout.domain.SetType;
import com.gymsync.api.workout.domain.WeightEntryMode;
import com.gymsync.api.workout.dto.CreateBatchWorkoutSetsRequest;
import com.gymsync.api.workout.dto.CreateWorkoutSetRequest;
import java.math.BigDecimal;

/** Resolves legacy {@code weight} vs {@code enteredWeight} into a normalized entry payload. */
final class WorkoutSetRequestResolver {

  record ResolvedEntry(
      BigDecimal enteredWeight,
      WeightEntryMode weightEntryMode,
      BigDecimal barWeight,
      SetType setType,
      Integer rir,
      String note,
      boolean completed) {}

  private WorkoutSetRequestResolver() {}

  static ResolvedEntry resolve(CreateWorkoutSetRequest request) {
    BigDecimal entered = request.enteredWeight();
    if (entered == null) {
      entered = request.weight();
    }
    if (entered == null) {
      throw new BadRequestException("Either weight or enteredWeight is required.");
    }
    WeightEntryMode mode =
        request.weightEntryMode() != null ? request.weightEntryMode() : WeightEntryMode.TOTAL;
    if (mode == WeightEntryMode.PER_SIDE && request.barWeight() == null) {
      throw new BadRequestException("barWeight is required when weightEntryMode is PER_SIDE.");
    }
    SetType setType = request.setType() != null ? request.setType() : SetType.WORKING;
    boolean completed = request.completed() == null || request.completed();
    return new ResolvedEntry(
        entered, mode, request.barWeight(), setType, request.rir(), request.note(), completed);
  }

  static ResolvedEntry resolve(CreateBatchWorkoutSetsRequest request) {
    BigDecimal entered = request.enteredWeight();
    if (entered == null) {
      entered = request.weight();
    }
    if (entered == null) {
      throw new BadRequestException("Either weight or enteredWeight is required.");
    }
    WeightEntryMode mode =
        request.weightEntryMode() != null ? request.weightEntryMode() : WeightEntryMode.TOTAL;
    if (mode == WeightEntryMode.PER_SIDE && request.barWeight() == null) {
      throw new BadRequestException("barWeight is required when weightEntryMode is PER_SIDE.");
    }
    SetType setType = request.setType() != null ? request.setType() : SetType.WORKING;
    boolean completed = request.completed() == null || request.completed();
    return new ResolvedEntry(
        entered, mode, request.barWeight(), setType, request.rir(), request.note(), completed);
  }
}
