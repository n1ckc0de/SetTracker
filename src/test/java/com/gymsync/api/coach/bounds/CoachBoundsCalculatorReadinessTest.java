package com.gymsync.api.coach.bounds;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymsync.api.coach.bounds.CoachBoundsCalculator.ReadinessTier;
import com.gymsync.api.coach.llm.CoachContext.ReadinessSnapshot;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CoachBoundsCalculatorReadinessTest {

  @Test
  void lowReadiness_proposedWeightNeverExceedsLastWeight() {
    BigDecimal lastWeight = new BigDecimal("100.00");
    CoachBoundsCalculator.WeightBounds bounds =
        CoachBoundsCalculator.weightAdjustmentBounds(lastWeight);
    ReadinessSnapshot low = new ReadinessSnapshot(null, 2, 2, 4, 2, null);

    ReadinessTier tier = CoachBoundsCalculator.classifyReadiness(low);
    assertThat(tier).isEqualTo(ReadinessTier.LOW);

    BigDecimal proposed = CoachBoundsCalculator.proposedWeightForTier(tier, lastWeight, bounds);

    assertThat(proposed).isLessThanOrEqualTo(lastWeight);
    assertThat(proposed).isGreaterThanOrEqualTo(bounds.minWeight());
  }

  @Test
  void highReadiness_mayProgressWithinBounds() {
    BigDecimal lastWeight = new BigDecimal("100.00");
    CoachBoundsCalculator.WeightBounds bounds =
        CoachBoundsCalculator.weightAdjustmentBounds(lastWeight);
    ReadinessSnapshot high = new ReadinessSnapshot(null, 5, 5, 1, 1, null);

    BigDecimal proposed =
        CoachBoundsCalculator.proposedWeightForTier(
            CoachBoundsCalculator.classifyReadiness(high), lastWeight, bounds);

    assertThat(proposed).isGreaterThanOrEqualTo(lastWeight);
    assertThat(proposed).isLessThanOrEqualTo(bounds.maxWeight());
  }

  @Test
  void targetRir_increasesAsReadinessDrops() {
    assertThat(CoachBoundsCalculator.targetRirForTier(ReadinessTier.HIGH)).isEqualTo(1);
    assertThat(CoachBoundsCalculator.targetRirForTier(ReadinessTier.MODERATE)).isEqualTo(2);
    assertThat(CoachBoundsCalculator.targetRirForTier(ReadinessTier.LOW)).isEqualTo(3);
  }
}
