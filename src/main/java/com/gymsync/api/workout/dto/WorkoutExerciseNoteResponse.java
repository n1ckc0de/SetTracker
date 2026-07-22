package com.gymsync.api.workout.dto;

import java.util.UUID;

public record WorkoutExerciseNoteResponse(UUID exerciseId, String note) {}
