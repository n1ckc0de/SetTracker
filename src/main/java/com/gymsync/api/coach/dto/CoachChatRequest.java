package com.gymsync.api.coach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CoachChatRequest(
    @NotBlank @Size(max = 2000) String message, UUID exerciseId, UUID workoutId) {}
