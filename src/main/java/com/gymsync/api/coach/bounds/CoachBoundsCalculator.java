package com.gymsync.api.coach.bounds;

import com.gymsync.api.coach.llm.CoachContext.ReadinessSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Deterministic guardrails for coach recommendations. Has <strong>no</strong> LLM dependency —
 * every numeric and safety rule is enforced here before any generative layer selects a value within
 * the computed bounds.
 */
public final class CoachBoundsCalculator {

  public static final BigDecimal MAX_WEIGHT_CHANGE_RATIO = new BigDecimal("0.10");
  public static final BigDecimal MAX_VOLUME_SET_REDUCTION_RATIO = new BigDecimal("0.20");
  public static final int MAX_SETS_REMOVED = 1;
  public static final int DECLINE_SESSIONS_REQUIRED = 3;
  public static final int HIGH_SORENESS_OR_STRESS = 4;

  private static final Pattern INJURY_KEYWORDS =
      Pattern.compile("\\b(pain|hurt|injury|sharp)\\b", Pattern.CASE_INSENSITIVE);

  private CoachBoundsCalculator() {}

  public static boolean containsInjuryOrMedicalConcern(String note) {
    if (note == null || note.isBlank()) {
      return false;
    }
    return INJURY_KEYWORDS.matcher(note.toLowerCase(Locale.ROOT)).find();
  }

  /**
   * Weight change must stay within ±10% of {@code lastWeight}. Returns inclusive min/max absolute
   * weights (never negative).
   */
  public static WeightBounds weightAdjustmentBounds(BigDecimal lastWeight) {
    Objects.requireNonNull(lastWeight, "lastWeight");
    if (lastWeight.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("lastWeight must be non-negative");
    }
    BigDecimal delta =
        lastWeight.multiply(MAX_WEIGHT_CHANGE_RATIO).setScale(2, RoundingMode.HALF_UP);
    BigDecimal min =
        lastWeight.subtract(delta).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    BigDecimal max = lastWeight.add(delta).setScale(2, RoundingMode.HALF_UP);
    return new WeightBounds(min, max, delta.negate(), delta);
  }

  /**
   * Volume: never recommend removing more than 1 set or 20% of total sets (whichever is smaller as
   * an absolute set count). Returns the maximum number of sets that may be removed (0+).
   */
  public static int maxSetsRemovable(int currentSetCount) {
    if (currentSetCount < 0) {
      throw new IllegalArgumentException("currentSetCount must be non-negative");
    }
    if (currentSetCount == 0) {
      return 0;
    }
    int twentyPercent =
        BigDecimal.valueOf(currentSetCount)
            .multiply(MAX_VOLUME_SET_REDUCTION_RATIO)
            .setScale(0, RoundingMode.DOWN)
            .intValue();
    return Math.min(MAX_SETS_REMOVED, twentyPercent);
  }

  /**
   * Deload requires both: (1) declining top-set performance across 3+ consecutive sessions, and (2)
   * soreness ≥ 4 OR stress ≥ 4 on a recent check-in.
   *
   * @param sessionTopWeights chronological oldest→newest top working weights (at least 3 for a
   *     possible true result)
   */
  public static boolean shouldRecommendDeload(
      List<BigDecimal> sessionTopWeights, Integer soreness, Integer stress) {
    boolean declining = isDecliningAcrossSessions(sessionTopWeights);
    boolean highLoad =
        (soreness != null && soreness >= HIGH_SORENESS_OR_STRESS)
            || (stress != null && stress >= HIGH_SORENESS_OR_STRESS);
    return declining && highLoad;
  }

  public static boolean isDecliningAcrossSessions(List<BigDecimal> sessionTopWeights) {
    if (sessionTopWeights == null || sessionTopWeights.size() < DECLINE_SESSIONS_REQUIRED) {
      return false;
    }
    int n = sessionTopWeights.size();
    List<BigDecimal> window = sessionTopWeights.subList(n - DECLINE_SESSIONS_REQUIRED, n);
    for (int i = 1; i < window.size(); i++) {
      if (window.get(i).compareTo(window.get(i - 1)) >= 0) {
        return false;
      }
    }
    return true;
  }

  public record WeightBounds(
      BigDecimal minWeight, BigDecimal maxWeight, BigDecimal minDelta, BigDecimal maxDelta) {}

  public enum ReadinessTier {
    HIGH,
    MODERATE,
    LOW
  }

  /** Maps latest readiness dimensions into bounded coaching tiers (deterministic, no LLM). */
  public static ReadinessTier classifyReadiness(ReadinessSnapshot readiness) {
    if (readiness == null) {
      return ReadinessTier.HIGH;
    }
    if (isLowReadiness(readiness)) {
      return ReadinessTier.LOW;
    }
    if (isModerateReadiness(readiness)) {
      return ReadinessTier.MODERATE;
    }
    return ReadinessTier.HIGH;
  }

  public static int targetRirForTier(ReadinessTier tier) {
    return switch (tier) {
      case HIGH -> 1;
      case MODERATE -> 2;
      case LOW -> 3;
    };
  }

  /**
   * Absolute proposed weight for a tier. LOW never exceeds {@code lastWeight}; HIGH/MODERATE stay
   * within {@code bounds}.
   */
  public static BigDecimal proposedWeightForTier(
      ReadinessTier tier, BigDecimal lastWeight, WeightBounds bounds) {
    Objects.requireNonNull(lastWeight, "lastWeight");
    Objects.requireNonNull(bounds, "bounds");
    return switch (tier) {
      case HIGH -> {
        BigDecimal step =
            bounds
                .maxDelta()
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);
        yield lastWeight.add(step).min(bounds.maxWeight()).setScale(2, RoundingMode.HALF_UP);
      }
      case MODERATE -> lastWeight.setScale(2, RoundingMode.HALF_UP);
      case LOW -> {
        BigDecimal step =
            bounds
                .minDelta()
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
                .min(BigDecimal.ZERO);
        yield lastWeight.add(step).max(bounds.minWeight()).setScale(2, RoundingMode.HALF_UP);
      }
    };
  }

  public static String readinessContextSummary(ReadinessSnapshot readiness, ReadinessTier tier) {
    if (readiness == null) {
      return "No recent readiness check-in — using performance history only.";
    }
    return "Readiness tier "
        + tier.name().toLowerCase(Locale.ROOT)
        + " (sleep="
        + readiness.sleepQuality()
        + ", energy="
        + readiness.energyLevel()
        + ", soreness="
        + readiness.soreness()
        + ", stress="
        + readiness.stress()
        + ").";
  }

  private static boolean isLowReadiness(ReadinessSnapshot r) {
    return (r.energyLevel() != null && r.energyLevel() <= 2)
        || (r.soreness() != null && r.soreness() >= 4)
        || (r.stress() != null && r.stress() >= 4)
        || (r.sleepQuality() != null && r.sleepQuality() <= 2);
  }

  private static boolean isModerateReadiness(ReadinessSnapshot r) {
    return (r.energyLevel() != null && r.energyLevel() == 3)
        || (r.soreness() != null && r.soreness() == 3)
        || (r.stress() != null && r.stress() == 3)
        || (r.sleepQuality() != null && r.sleepQuality() == 3);
  }
}
