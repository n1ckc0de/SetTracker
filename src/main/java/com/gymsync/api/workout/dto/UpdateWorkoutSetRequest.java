package com.gymsync.api.workout.dto;

import com.gymsync.api.workout.domain.SetType;
import com.gymsync.api.workout.domain.WeightEntryMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateWorkoutSetRequest(
    @NotNull(message = "enteredWeight is required")
        @DecimalMin(value = "0", message = "enteredWeight must be non-negative")
        BigDecimal enteredWeight,
    @NotNull(message = "weightEntryMode is required") WeightEntryMode weightEntryMode,
    @DecimalMin(value = "0", message = "barWeight must be non-negative") BigDecimal barWeight,
    @NotNull(message = "reps is required") @Positive(message = "reps must be greater than zero")
        Integer reps,
    @NotNull(message = "setNumber is required")
        @Positive(message = "setNumber must be greater than zero")
        Integer setNumber,
    SetType setType,
    @Min(value = 0, message = "rir must be between 0 and 5")
        @Max(value = 5, message = "rir must be between 0 and 5")
        Integer rir,
    @Size(max = 280, message = "note must be at most 280 characters") String note,
    Boolean completed) {}
