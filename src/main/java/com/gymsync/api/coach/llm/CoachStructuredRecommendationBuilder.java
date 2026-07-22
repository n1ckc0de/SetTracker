package com.gymsync.api.coach.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gymsync.api.coach.bounds.CoachBoundsCalculator;
import com.gymsync.api.coach.bounds.CoachBoundsCalculator.ReadinessTier;
import com.gymsync.api.coach.domain.ConfidenceLevel;
import com.gymsync.api.coach.domain.RecommendationType;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Builds structured coach proposals with readiness-aware absolute targets. */
@Component
public class CoachStructuredRecommendationBuilder {

  private final ObjectMapper objectMapper;

  public CoachStructuredRecommendationBuilder(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public GeneratedCoachAdvice buildStructured(
      CoachContext context, CoachAdviceNormalizer normalizer) {
    if (context.weightBounds() == null || context.recentSessionTopWeights().isEmpty()) {
      return generalAdvice(context, normalizer);
    }

    BigDecimal lastWeight =
        context.recentSessionTopWeights().get(context.recentSessionTopWeights().size() - 1);
    ReadinessTier tier = CoachBoundsCalculator.classifyReadiness(context.readiness());
    BigDecimal proposedWeight =
        CoachBoundsCalculator.proposedWeightForTier(tier, lastWeight, context.weightBounds());
    int targetRir = CoachBoundsCalculator.targetRirForTier(tier);
    int proposedSets = context.recentSetCount() != null ? Math.max(1, context.recentSetCount()) : 3;
    int repMin = 6;
    int repMax = 8;
    if (tier == ReadinessTier.MODERATE) {
      repMin = 6;
      repMax = 6;
    } else if (tier == ReadinessTier.LOW) {
      proposedSets =
          Math.max(1, proposedSets - CoachBoundsCalculator.maxSetsRemovable(proposedSets));
      repMin = 8;
      repMax = 10;
    }

    ObjectNode change = objectMapper.createObjectNode();
    change.put("action", "ADJUST");
    change.put("proposedWeight", proposedWeight);
    change.put("proposedSets", proposedSets);
    change.put("proposedRepRangeMin", repMin);
    change.put("proposedRepRangeMax", repMax);
    change.put("targetRir", targetRir);
    change.put(
        "readinessContextSummary",
        CoachBoundsCalculator.readinessContextSummary(context.readiness(), tier));
    change.put(
        "performanceContextSummary",
        "Last top set "
            + lastWeight
            + " lb across "
            + context.recentSessionTopWeights().size()
            + " recent sessions for "
            + context.exerciseName()
            + ".");
    BigDecimal delta = proposedWeight.subtract(lastWeight);
    change.put("weightDelta", delta);

    RecommendationType type =
        delta.compareTo(BigDecimal.ZERO) < 0
            ? RecommendationType.WEIGHT_ADJUSTMENT
            : delta.compareTo(BigDecimal.ZERO) > 0
                ? RecommendationType.WEIGHT_ADJUSTMENT
                : RecommendationType.GENERAL;
    ConfidenceLevel confidence =
        tier == ReadinessTier.LOW ? ConfidenceLevel.MEDIUM : ConfidenceLevel.HIGH;
    String reasoning =
        tier == ReadinessTier.LOW
            ? "Low readiness — conservative load and higher RIR within bounds."
            : tier == ReadinessTier.MODERATE
                ? "Moderate readiness — repeat last load at the low end of the rep range."
                : "High readiness — small progression within the ±10% bound.";

    GeneratedCoachAdvice raw =
        new GeneratedCoachAdvice(
            type,
            normalizer.buildPreviousPerformance(context),
            change.toString(),
            reasoning,
            confidence);
    return normalizer.normalize(raw, context);
  }

  private GeneratedCoachAdvice generalAdvice(
      CoachContext context, CoachAdviceNormalizer normalizer) {
    ObjectNode change = objectMapper.createObjectNode();
    change.put("action", "MAINTAIN");
    change.put(
        "readinessContextSummary",
        CoachBoundsCalculator.readinessContextSummary(
            context.readiness(), CoachBoundsCalculator.classifyReadiness(context.readiness())));
    change.put("performanceContextSummary", "Insufficient logged history for load targets.");
    return normalizer.normalize(
        new GeneratedCoachAdvice(
            RecommendationType.GENERAL,
            normalizer.buildPreviousPerformance(context),
            change.toString(),
            "Not enough exercise history for a load change. Keep training consistent and log sets.",
            ConfidenceLevel.LOW),
        context);
  }
}
