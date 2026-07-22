package com.gymsync.api.program.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ProgramDayExerciseInput(
    @NotNull UUID exerciseId,
    @NotNull @Min(1) Integer orderInDay,
    @NotNull @Min(1) Integer targetSets,
    @Min(1) Integer targetRepsMin,
    @Min(1) Integer targetRepsMax,
    @DecimalMin("0.0") BigDecimal targetWeight) {}
