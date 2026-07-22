package com.gymsync.api.coach.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsync.api.coach.bounds.CoachBoundsCalculator;
import com.gymsync.api.coach.domain.ConfidenceLevel;
import com.gymsync.api.coach.domain.RecommendationType;
import com.gymsync.api.common.exception.BadRequestException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoachAdviceNormalizerTest {

  private CoachAdviceNormalizer normalizer;
  private CoachContext context;

  @BeforeEach
  void setUp() {
    normalizer = new CoachAdviceNormalizer(new ObjectMapper());
    CoachBoundsCalculator.WeightBounds bounds =
        CoachBoundsCalculator.weightAdjustmentBounds(new BigDecimal("100.00"));
    context =
        new CoachContext(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            "Bench Press",
            List.of(new BigDecimal("100")),
            5,
            null,
            bounds,
            1,
            false,
            false,
            null);
  }

  @Test
  void clampsOutOfBoundsWeightDelta() {
    GeneratedCoachAdvice raw =
        new GeneratedCoachAdvice(
            RecommendationType.WEIGHT_ADJUSTMENT,
            "{}",
            "{\"weightDelta\":50}",
            "Go heavy",
            ConfidenceLevel.HIGH);
    GeneratedCoachAdvice normalized = normalizer.normalize(raw, context);
    assertThat(normalized.proposedChangeJson()).contains("\"weightDelta\":10.00");
  }

  @Test
  void clampsSetsDeltaToMaxRemovable() {
    GeneratedCoachAdvice raw =
        new GeneratedCoachAdvice(
            RecommendationType.VOLUME_ADJUSTMENT,
            "{}",
            "{\"setsDelta\":-5}",
            "Cut volume",
            ConfidenceLevel.MEDIUM);
    GeneratedCoachAdvice normalized = normalizer.normalize(raw, context);
    assertThat(normalized.proposedChangeJson()).contains("\"setsDelta\":-1");
  }

  @Test
  void rejectsMalformedProposedChange() {
    GeneratedCoachAdvice raw =
        new GeneratedCoachAdvice(
            RecommendationType.GENERAL, "{}", "not-json", "hmm", ConfidenceLevel.LOW);
    assertThatThrownBy(() -> normalizer.normalize(raw, context))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void safetyHoldUsesFixedReasoning() {
    GeneratedCoachAdvice advice = normalizer.safetyHoldAdvice("{}");
    assertThat(advice.type()).isEqualTo(RecommendationType.SAFETY_HOLD);
    assertThat(advice.reasoning()).isEqualTo(CoachAdviceNormalizer.SAFETY_REASONING);
    assertThat(advice.proposedChangeJson()).contains("seekProfessional");
  }
}
