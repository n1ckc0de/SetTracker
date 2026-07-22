package com.gymsync.api.stats.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Personal records for a single exercise, computed only from confirmed, completed workouts (spec
 * section 14). {@code estimatedOneRepMax} uses the Epley formula ({@code weight * (1 + reps / 30)})
 * and is explicitly informational, not a measured max.
 */
public record PersonalRecordResponse(
    UUID exerciseId,
    BigDecimal heaviestWeight,
    Integer repsAtHeaviestWeight,
    Integer highestRepsAtHeaviestWeight,
    Instant heaviestWeightAchievedAt,
    UUID heaviestWeightWorkoutId,
    BigDecimal estimatedOneRepMax,
    Instant estimatedOneRepMaxAchievedAt,
    UUID estimatedOneRepMaxWorkoutId,
    String disclaimer) {}
