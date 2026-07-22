package com.gymsync.api.exercise.dto;

import com.gymsync.api.coach.dto.CoachRecommendationResponse;
import com.gymsync.api.stats.dto.PersonalRecordResponse;
import com.gymsync.api.stats.dto.PrHistoryResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExerciseDetailResponse(
    ExerciseResponse exercise,
    PersonalRecordResponse personalRecord,
    PrHistoryResponse prHistory,
    List<RecentSetRow> recentSets,
    CoachRecommendationResponse activeRecommendation) {

  public record RecentSetRow(
      UUID setId, UUID workoutId, Instant loggedAt, BigDecimal weight, int reps, int setNumber) {}
}
