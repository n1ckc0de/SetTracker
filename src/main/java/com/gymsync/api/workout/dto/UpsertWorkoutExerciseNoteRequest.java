package com.gymsync.api.workout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertWorkoutExerciseNoteRequest(
    @NotBlank(message = "note is required")
        @Size(max = 500, message = "note must be at most 500 characters")
        String note) {}
