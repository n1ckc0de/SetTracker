package com.gymsync.api.stats.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Weekly training summary for {@code GET /api/stats/weekly}. Only confirmed, completed workouts are
 * counted (spec section 15). {@code volume = weight x reps}; bodyweight/cardio sets logged with
 * {@code weight = 0} contribute their reps/set counts but zero volume — see docs/API_DESIGN.md for
 * the rationale.
 */
public record WeeklyStatsResponse(
    LocalDate weekStart,
    LocalDate weekEnd,
    long completedWorkouts,
    long totalSets,
    long totalReps,
    BigDecimal totalVolume,
    List<ExerciseVolume> volumeByExercise,
    List<CategoryVolume> volumeByCategory,
    WeeklyComparison comparisonWithPriorWeek) {}
