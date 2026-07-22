package com.gymsync.api.stats.dto;

import java.util.UUID;

/**
 * Response for {@code GET /api/exercises/{exerciseId}/next-session-recommendation}. This is a
 * deterministic, rule-based suggestion derived only from the user's own logged history — not
 * medical or professional training advice (spec section 16).
 */
public record NextSessionRecommendationResponse(
    UUID exerciseId,
    UUID basedOnWorkoutId,
    PreviousPerformance previousPerformance,
    RecommendationOptions recommendation,
    String reason,
    String disclaimer) {}
