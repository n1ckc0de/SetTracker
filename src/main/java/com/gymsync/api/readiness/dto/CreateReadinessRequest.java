package com.gymsync.api.readiness.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateReadinessRequest(
    UUID workoutId,
    @DecimalMin("0.0") @DecimalMax("24.0") BigDecimal sleepHours,
    @Min(1) @Max(5) Integer sleepQuality,
    @Min(1) @Max(5) Integer energyLevel,
    @Min(1) @Max(5) Integer soreness,
    @Min(1) @Max(5) Integer stress,
    @Size(max = 500) String note) {}
