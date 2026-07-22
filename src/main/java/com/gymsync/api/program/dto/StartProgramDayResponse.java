package com.gymsync.api.program.dto;

import com.gymsync.api.workout.dto.WorkoutResponse;
import java.util.List;

/**
 * Result of starting a program day: a new ACTIVE workout plus the day's planned exercises as
 * guidance only (no WorkoutSet rows are fabricated).
 */
public record StartProgramDayResponse(
    WorkoutResponse workout,
    List<ProgramDetailResponse.PlannedExerciseResponse> plannedExercises) {}
