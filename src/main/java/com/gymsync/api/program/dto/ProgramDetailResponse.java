package com.gymsync.api.program.dto;

import com.gymsync.api.program.domain.Program;
import com.gymsync.api.program.domain.ProgramDay;
import com.gymsync.api.program.domain.ProgramDayExercise;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProgramDetailResponse(
    UUID id,
    String name,
    String description,
    List<ProgramDayResponse> days,
    Instant createdAt,
    Instant updatedAt) {

  public static ProgramDetailResponse from(Program program) {
    return new ProgramDetailResponse(
        program.getId(),
        program.getName(),
        program.getDescription(),
        program.getDays().stream().map(ProgramDayResponse::from).toList(),
        program.getCreatedAt(),
        program.getUpdatedAt());
  }

  public record ProgramDayResponse(
      UUID id, int dayNumber, String name, List<PlannedExerciseResponse> exercises) {

    public static ProgramDayResponse from(ProgramDay day) {
      return new ProgramDayResponse(
          day.getId(),
          day.getDayNumber(),
          day.getName(),
          day.getExercises().stream().map(PlannedExerciseResponse::from).toList());
    }
  }

  public record PlannedExerciseResponse(
      UUID id,
      UUID exerciseId,
      String exerciseName,
      int orderInDay,
      int targetSets,
      Integer targetRepsMin,
      Integer targetRepsMax,
      BigDecimal targetWeight) {

    public static PlannedExerciseResponse from(ProgramDayExercise item) {
      return new PlannedExerciseResponse(
          item.getId(),
          item.getExercise().getId(),
          item.getExercise().getName(),
          item.getOrderInDay(),
          item.getTargetSets(),
          item.getTargetRepsMin(),
          item.getTargetRepsMax(),
          item.getTargetWeight());
    }
  }
}
