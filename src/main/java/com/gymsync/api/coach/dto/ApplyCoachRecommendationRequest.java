package com.gymsync.api.coach.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ApplyCoachRecommendationRequest(@NotNull UUID workoutId) {}
