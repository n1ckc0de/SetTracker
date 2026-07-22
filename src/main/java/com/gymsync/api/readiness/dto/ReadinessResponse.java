package com.gymsync.api.readiness.dto;

import com.gymsync.api.readiness.domain.ReadinessCheckIn;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReadinessResponse(
    UUID id,
    UUID workoutId,
    BigDecimal sleepHours,
    Integer sleepQuality,
    Integer energyLevel,
    Integer soreness,
    Integer stress,
    String note,
    Instant createdAt) {

  public static ReadinessResponse from(ReadinessCheckIn entity) {
    return new ReadinessResponse(
        entity.getId(),
        entity.getWorkoutId(),
        entity.getSleepHours(),
        entity.getSleepQuality(),
        entity.getEnergyLevel(),
        entity.getSoreness(),
        entity.getStress(),
        entity.getNote(),
        entity.getCreatedAt());
  }
}
