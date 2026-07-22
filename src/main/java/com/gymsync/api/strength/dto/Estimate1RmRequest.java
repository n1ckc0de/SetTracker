package com.gymsync.api.strength.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record Estimate1RmRequest(
    String exerciseName,
    @NotNull(message = "weight is required")
        @DecimalMin(value = "0", message = "weight must be non-negative")
        BigDecimal weight,
    @NotNull(message = "reps is required") @Positive(message = "reps must be greater than zero")
        Integer reps,
    @Min(value = 0, message = "rir must be between 0 and 5")
        @Max(value = 5, message = "rir must be between 0 and 5")
        Integer rir) {}
