package com.gymsync.api.program.dto;

import com.gymsync.api.program.domain.Program;
import java.time.Instant;
import java.util.UUID;

public record ProgramSummaryResponse(
    UUID id, String name, String description, int dayCount, Instant createdAt, Instant updatedAt) {

  public static ProgramSummaryResponse from(Program program) {
    return new ProgramSummaryResponse(
        program.getId(),
        program.getName(),
        program.getDescription(),
        program.getDays().size(),
        program.getCreatedAt(),
        program.getUpdatedAt());
  }
}
