package com.gymsync.api.workout.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Computes normalized total load from user-entered values. */
public final class SetWeightNormalizer {

  private SetWeightNormalizer() {}

  public static BigDecimal computeTotalWeight(
      BigDecimal enteredWeight, WeightEntryMode mode, BigDecimal barWeight) {
    Objects.requireNonNull(enteredWeight, "enteredWeight");
    Objects.requireNonNull(mode, "mode");
    if (enteredWeight.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("enteredWeight must be non-negative");
    }
    if (mode == WeightEntryMode.TOTAL) {
      return enteredWeight.setScale(2, RoundingMode.HALF_UP);
    }
    BigDecimal bar = barWeight != null ? barWeight : BigDecimal.ZERO;
    if (bar.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("barWeight must be non-negative");
    }
    return bar.add(enteredWeight.multiply(BigDecimal.valueOf(2))).setScale(2, RoundingMode.HALF_UP);
  }
}
