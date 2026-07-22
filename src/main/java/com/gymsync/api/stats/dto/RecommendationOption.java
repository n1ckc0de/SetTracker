package com.gymsync.api.stats.dto;

import java.math.BigDecimal;

public record RecommendationOption(BigDecimal weight, Integer targetReps) {}
