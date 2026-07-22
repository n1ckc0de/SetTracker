package com.gymsync.api.coach.dto;

import java.util.UUID;

public record GenerateCoachRequest(UUID exerciseId, UUID workoutId) {}
