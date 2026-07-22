package com.gymsync.api.stats.dto;

import java.math.BigDecimal;

/**
 * Comparison against the prior calendar week. {@code volumeChangePercent} is {@code null} when the
 * prior week had zero volume (nothing to compute a percentage change against).
 */
public record WeeklyComparison(
    BigDecimal priorWeekVolume, BigDecimal volumeChange, Double volumeChangePercent) {}
