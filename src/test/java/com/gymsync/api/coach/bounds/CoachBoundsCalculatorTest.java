package com.gymsync.api.coach.bounds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoachBoundsCalculatorTest {

  @Test
  void weightBounds_arePlusMinusTenPercent() {
    CoachBoundsCalculator.WeightBounds bounds =
        CoachBoundsCalculator.weightAdjustmentBounds(new BigDecimal("100.00"));
    assertThat(bounds.minWeight()).isEqualByComparingTo("90.00");
    assertThat(bounds.maxWeight()).isEqualByComparingTo("110.00");
    assertThat(bounds.minDelta()).isEqualByComparingTo("-10.00");
    assertThat(bounds.maxDelta()).isEqualByComparingTo("10.00");
  }

  @Test
  void weightBounds_neverGoNegative() {
    // ±10% of a non-negative last weight stays non-negative; zero still clamps cleanly.
    CoachBoundsCalculator.WeightBounds zero =
        CoachBoundsCalculator.weightAdjustmentBounds(BigDecimal.ZERO);
    assertThat(zero.minWeight()).isEqualByComparingTo("0.00");
    assertThat(zero.maxWeight()).isEqualByComparingTo("0.00");

    CoachBoundsCalculator.WeightBounds small =
        CoachBoundsCalculator.weightAdjustmentBounds(new BigDecimal("5.00"));
    assertThat(small.minWeight()).isEqualByComparingTo("4.50");
    assertThat(small.maxWeight()).isEqualByComparingTo("5.50");
  }

  @Test
  void weightBounds_rejectNegativeLastWeight() {
    assertThatThrownBy(() -> CoachBoundsCalculator.weightAdjustmentBounds(new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void maxSetsRemovable_cappedAtOneAndTwentyPercent() {
    assertThat(CoachBoundsCalculator.maxSetsRemovable(0)).isZero();
    assertThat(CoachBoundsCalculator.maxSetsRemovable(3)).isZero(); // 20% of 3 floors to 0
    assertThat(CoachBoundsCalculator.maxSetsRemovable(5)).isEqualTo(1); // min(1, 1)
    assertThat(CoachBoundsCalculator.maxSetsRemovable(10)).isEqualTo(1); // min(1, 2) = 1
    assertThat(CoachBoundsCalculator.maxSetsRemovable(20)).isEqualTo(1);
  }

  @Test
  void deload_requiresBothDeclineAndHighSorenessOrStress() {
    List<BigDecimal> declining =
        List.of(new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("90"));
    assertThat(CoachBoundsCalculator.shouldRecommendDeload(declining, 4, 2)).isTrue();
    assertThat(CoachBoundsCalculator.shouldRecommendDeload(declining, 2, 4)).isTrue();
    assertThat(CoachBoundsCalculator.shouldRecommendDeload(declining, 3, 3)).isFalse();
    assertThat(CoachBoundsCalculator.shouldRecommendDeload(declining, null, null)).isFalse();
  }

  @Test
  void deload_falseWhenNotDecliningEvenWithHighSoreness() {
    List<BigDecimal> flat =
        List.of(new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"));
    List<BigDecimal> improving =
        List.of(new BigDecimal("90"), new BigDecimal("95"), new BigDecimal("100"));
    assertThat(CoachBoundsCalculator.shouldRecommendDeload(flat, 5, 5)).isFalse();
    assertThat(CoachBoundsCalculator.shouldRecommendDeload(improving, 5, 5)).isFalse();
  }

  @Test
  void decline_requiresThreeStrictlyDecreasingSessions() {
    assertThat(
            CoachBoundsCalculator.isDecliningAcrossSessions(
                List.of(new BigDecimal("100"), new BigDecimal("95"))))
        .isFalse();
    assertThat(
            CoachBoundsCalculator.isDecliningAcrossSessions(
                List.of(
                    new BigDecimal("110"),
                    new BigDecimal("100"),
                    new BigDecimal("95"),
                    new BigDecimal("90"))))
        .isTrue();
  }

  @Test
  void injuryKeywords_detectedCaseInsensitiveAsWordBoundaries() {
    assertThat(CoachBoundsCalculator.containsInjuryOrMedicalConcern("Sharp pain in shoulder"))
        .isTrue();
    assertThat(CoachBoundsCalculator.containsInjuryOrMedicalConcern("my knee HURT yesterday"))
        .isTrue();
    assertThat(CoachBoundsCalculator.containsInjuryOrMedicalConcern("possible injury")).isTrue();
    assertThat(CoachBoundsCalculator.containsInjuryOrMedicalConcern("felt painted")).isFalse();
    assertThat(CoachBoundsCalculator.containsInjuryOrMedicalConcern("normal soreness")).isFalse();
    assertThat(CoachBoundsCalculator.containsInjuryOrMedicalConcern(null)).isFalse();
    assertThat(CoachBoundsCalculator.containsInjuryOrMedicalConcern("  ")).isFalse();
  }
}
