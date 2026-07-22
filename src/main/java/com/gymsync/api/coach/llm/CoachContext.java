package com.gymsync.api.coach.llm;

import com.gymsync.api.coach.bounds.CoachBoundsCalculator;
import com.gymsync.api.readiness.domain.ReadinessCheckIn;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Inputs for {@link CoachRecommendationGenerator}. Bounds are precomputed by application code. */
public record CoachContext(
    UUID userId,
    UUID exerciseId,
    UUID basedOnWorkoutId,
    UUID readinessCheckInId,
    String exerciseName,
    List<BigDecimal> recentSessionTopWeights,
    Integer recentSetCount,
    ReadinessSnapshot readiness,
    CoachBoundsCalculator.WeightBounds weightBounds,
    int maxSetsRemovable,
    boolean deloadEligible,
    boolean safetyHold,
    String safetyReason) {

  public record ReadinessSnapshot(
      BigDecimal sleepHours,
      Integer sleepQuality,
      Integer energyLevel,
      Integer soreness,
      Integer stress,
      String note) {

    public static ReadinessSnapshot from(ReadinessCheckIn checkIn) {
      if (checkIn == null) {
        return null;
      }
      return new ReadinessSnapshot(
          checkIn.getSleepHours(),
          checkIn.getSleepQuality(),
          checkIn.getEnergyLevel(),
          checkIn.getSoreness(),
          checkIn.getStress(),
          checkIn.getNote());
    }
  }
}
