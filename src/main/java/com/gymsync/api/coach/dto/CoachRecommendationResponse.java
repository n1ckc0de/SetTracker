package com.gymsync.api.coach.dto;

import com.gymsync.api.coach.domain.CoachRecommendation;
import com.gymsync.api.coach.domain.ConfidenceLevel;
import com.gymsync.api.coach.domain.RecommendationStatus;
import com.gymsync.api.coach.domain.RecommendationType;
import java.time.Instant;
import java.util.UUID;

public record CoachRecommendationResponse(
    UUID id,
    UUID exerciseId,
    UUID basedOnWorkoutId,
    UUID readinessCheckInId,
    RecommendationType recommendationType,
    String previousPerformance,
    String proposedChange,
    String reasoning,
    ConfidenceLevel confidenceLevel,
    RecommendationStatus status,
    Instant createdAt,
    Instant respondedAt) {

  public static CoachRecommendationResponse from(CoachRecommendation r) {
    return new CoachRecommendationResponse(
        r.getId(),
        r.getExerciseId(),
        r.getBasedOnWorkoutId(),
        r.getReadinessCheckInId(),
        r.getRecommendationType(),
        r.getPreviousPerformance(),
        r.getProposedChange(),
        r.getReasoning(),
        r.getConfidenceLevel(),
        r.getStatus(),
        r.getCreatedAt(),
        r.getRespondedAt());
  }
}
