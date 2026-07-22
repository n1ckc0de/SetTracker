package com.gymsync.api.exercise.dto;

import com.gymsync.api.exercise.domain.ExerciseCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateExerciseRequest(
    @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,
    @NotNull(message = "category is required") ExerciseCategory category,
    @Size(max = 2048) String thumbnailUrl,
    @Size(max = 2048) String demoVideoUrl,
    @Size(max = 500) String mediaAttribution) {}
