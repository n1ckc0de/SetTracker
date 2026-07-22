package com.gymsync.api.stats.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PrHistoryResponse(UUID exerciseId, List<PrHistoryPoint> points, String disclaimer) {

  public record PrHistoryPoint(
      Instant at, UUID workoutId, BigDecimal weight, int reps, BigDecimal estimatedOneRepMax) {}
}
