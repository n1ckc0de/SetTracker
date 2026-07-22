package com.gymsync.api.workout.dto;

import java.math.BigDecimal;

public record PrDetectionResult(
    boolean weightPr,
    boolean estimatedOneRepMaxPr,
    BigDecimal previousBestWeight,
    BigDecimal previousBestEstimatedOneRepMax) {}
