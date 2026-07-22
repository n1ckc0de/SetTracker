package com.gymsync.api.program.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateProgramDayRequest(
    @NotBlank @Size(max = 255) String name,
    @NotNull @Valid @Size(min = 1) List<ProgramDayExerciseInput> exercises) {}
