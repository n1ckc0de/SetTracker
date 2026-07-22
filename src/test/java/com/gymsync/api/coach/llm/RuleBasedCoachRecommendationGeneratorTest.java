package com.gymsync.api.coach.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsync.api.coach.bounds.CoachBoundsCalculator;
import com.gymsync.api.coach.domain.RecommendationType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuleBasedCoachRecommendationGeneratorTest {

  private RuleBasedCoachRecommendationGenerator generator;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    CoachAdviceNormalizer normalizer = new CoachAdviceNormalizer(objectMapper);
    generator =
        new RuleBasedCoachRecommendationGenerator(
            objectMapper, normalizer, new CoachStructuredRecommendationBuilder(objectMapper));
  }

  @Test
  void progressAdvice_staysWithinWeightBounds() throws Exception {
    CoachBoundsCalculator.WeightBounds bounds =
        CoachBoundsCalculator.weightAdjustmentBounds(new BigDecimal("100.00"));
    CoachContext context =
        new CoachContext(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            "Bench Press",
            List.of(new BigDecimal("100")),
            5,
            new CoachContext.ReadinessSnapshot(null, 4, 4, 2, 2, null),
            bounds,
            CoachBoundsCalculator.maxSetsRemovable(5),
            false,
            false,
            null);

    GeneratedCoachAdvice advice = generator.generate(context);
    assertThat(advice.type()).isEqualTo(RecommendationType.WEIGHT_ADJUSTMENT);
    JsonNode change = objectMapper.readTree(advice.proposedChangeJson());
    BigDecimal proposed = change.get("proposedWeight").decimalValue();
    assertThat(proposed).isBetween(bounds.minWeight(), bounds.maxWeight());
    assertThat(change.get("weightDelta").decimalValue()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
  }

  @Test
  void reduceAdvice_respectsLowReadinessStructuredTargets() throws Exception {
    CoachBoundsCalculator.WeightBounds bounds =
        CoachBoundsCalculator.weightAdjustmentBounds(new BigDecimal("100.00"));
    int maxRemovable = CoachBoundsCalculator.maxSetsRemovable(10);
    CoachContext context =
        new CoachContext(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            "Squat",
            List.of(new BigDecimal("100")),
            10,
            new CoachContext.ReadinessSnapshot(null, 2, 2, 4, 2, null),
            bounds,
            maxRemovable,
            false,
            false,
            null);

    GeneratedCoachAdvice advice = generator.generate(context);
    JsonNode change = objectMapper.readTree(advice.proposedChangeJson());
    assertThat(change.has("readinessContextSummary")).isTrue();
    assertThat(change.get("readinessContextSummary").asText()).containsIgnoringCase("low");
    assertThat(change.get("proposedWeight").decimalValue())
        .isLessThanOrEqualTo(new BigDecimal("100"));
    assertThat(change.get("proposedSets").asInt()).isLessThanOrEqualTo(10);
  }

  @Test
  void deloadAdvice_whenEligible() throws Exception {
    List<BigDecimal> declining =
        List.of(new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("90"));
    CoachBoundsCalculator.WeightBounds bounds =
        CoachBoundsCalculator.weightAdjustmentBounds(new BigDecimal("90.00"));
    CoachContext context =
        new CoachContext(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            "Bench",
            declining,
            5,
            new CoachContext.ReadinessSnapshot(null, 3, 3, 4, 2, null),
            bounds,
            1,
            true,
            false,
            null);

    GeneratedCoachAdvice advice = generator.generate(context);
    assertThat(advice.type()).isEqualTo(RecommendationType.DELOAD);
    JsonNode change = objectMapper.readTree(advice.proposedChangeJson());
    assertThat(change.path("action").asText()).isEqualTo("DELOAD");
    if (change.has("weightDelta")) {
      assertThat(change.get("weightDelta").decimalValue())
          .isBetween(bounds.minDelta(), bounds.maxDelta());
    }
  }

  @Test
  void generalAdvice_whenNoWeightHistory() {
    CoachContext context =
        new CoachContext(
            UUID.randomUUID(),
            null,
            null,
            null,
            null,
            List.of(),
            null,
            null,
            null,
            0,
            false,
            false,
            null);
    GeneratedCoachAdvice advice = generator.generate(context);
    assertThat(advice.type()).isEqualTo(RecommendationType.GENERAL);
    assertThat(advice.proposedChangeJson()).contains("MAINTAIN");
  }

  @Test
  void safetyHoldContext_returnsFixedSafetyAdvice() {
    CoachContext context =
        new CoachContext(
            UUID.randomUUID(),
            null,
            null,
            null,
            null,
            List.of(),
            null,
            null,
            null,
            0,
            false,
            true,
            CoachAdviceNormalizer.SAFETY_REASONING);
    GeneratedCoachAdvice advice = generator.generate(context);
    assertThat(advice.type()).isEqualTo(RecommendationType.SAFETY_HOLD);
    assertThat(advice.reasoning()).isEqualTo(CoachAdviceNormalizer.SAFETY_REASONING);
  }

  @Test
  void weightDeltaAtBoundary_neverExceedsTenPercent() throws Exception {
    BigDecimal last = new BigDecimal("200.00");
    CoachBoundsCalculator.WeightBounds bounds = CoachBoundsCalculator.weightAdjustmentBounds(last);
    CoachContext context =
        new CoachContext(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            "Deadlift",
            List.of(last),
            4,
            new CoachContext.ReadinessSnapshot(null, 4, 4, 1, 1, null),
            bounds,
            0,
            false,
            false,
            null);
    GeneratedCoachAdvice advice = generator.generate(context);
    JsonNode change = objectMapper.readTree(advice.proposedChangeJson());
    BigDecimal proposed = change.get("proposedWeight").decimalValue();
    assertThat(proposed).isBetween(bounds.minWeight(), bounds.maxWeight());
  }

  @Test
  void structuredAdvice_includesReadinessContextSummary() throws Exception {
    CoachBoundsCalculator.WeightBounds bounds =
        CoachBoundsCalculator.weightAdjustmentBounds(new BigDecimal("100.00"));
    CoachContext context =
        new CoachContext(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            "Row",
            List.of(new BigDecimal("100")),
            4,
            new CoachContext.ReadinessSnapshot(null, 2, 2, 4, 3, null),
            bounds,
            1,
            false,
            false,
            null);

    GeneratedCoachAdvice advice = generator.generate(context);
    JsonNode change = objectMapper.readTree(advice.proposedChangeJson());
    assertThat(change.path("readinessContextSummary").asText()).contains("soreness=4");
  }
}
