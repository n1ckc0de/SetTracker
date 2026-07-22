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
import java.util.UUID;

public record CreateBatchWorkoutSetsRequest(
    @NotNull(message = "exerciseId is required") UUID exerciseId,
    @NotNull(message = "count is required")
        @Positive(message = "count must be greater than zero")
        @Max(value = 20, message = "count must be at most 20")
        Integer count,
    @DecimalMin(value = "0", message = "weight must be non-negative") BigDecimal weight,
    @DecimalMin(value = "0", message = "enteredWeight must be non-negative")
        BigDecimal enteredWeight,
    WeightEntryMode weightEntryMode,
    @DecimalMin(value = "0", message = "barWeight must be non-negative") BigDecimal barWeight,
    @NotNull(message = "reps is required") @Positive(message = "reps must be greater than zero")
        Integer reps,
    SetType setType,
    @Min(value = 0, message = "rir must be between 0 and 5")
        @Max(value = 5, message = "rir must be between 0 and 5")
        Integer rir,
    @Size(max = 280, message = "note must be at most 280 characters") String note,
    Boolean completed,
    @Positive(message = "startingSetNumber must be greater than zero") Integer startingSetNumber) {}
