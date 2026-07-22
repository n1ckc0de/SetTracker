package com.gymsync.api.coach.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gymsync.api.coach.domain.ConfidenceLevel;
import com.gymsync.api.coach.domain.RecommendationType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic coach generator for local development and tests. Selects values strictly within
 * precomputed {@link CoachContext} bounds — no LLM required.
 */
@Component
@ConditionalOnProperty(
    prefix = "gymsync.llm",
    name = "provider",
    havingValue = "rule-based",
    matchIfMissing = true)
public class RuleBasedCoachRecommendationGenerator implements CoachRecommendationGenerator {

  private final ObjectMapper objectMapper;
  private final CoachAdviceNormalizer normalizer;
  private final CoachStructuredRecommendationBuilder structuredBuilder;

  public RuleBasedCoachRecommendationGenerator(
      ObjectMapper objectMapper,
      CoachAdviceNormalizer normalizer,
      CoachStructuredRecommendationBuilder structuredBuilder) {
    this.objectMapper = objectMapper;
    this.normalizer = normalizer;
    this.structuredBuilder = structuredBuilder;
  }

  @Override
  public GeneratedCoachAdvice generate(CoachContext context) {
    if (context.safetyHold()) {
      // Service should short-circuit before calling; keep a safe fallback.
      return normalizer.safetyHoldAdvice(normalizer.buildPreviousPerformance(context));
    }

    String previous = normalizer.buildPreviousPerformance(context);
    GeneratedCoachAdvice raw;
    if (context.deloadEligible()) {
      raw = deloadAdvice(context, previous);
    } else if (context.weightBounds() != null) {
      return structuredBuilder.buildStructured(context, normalizer);
    } else {
      raw = generalAdvice(previous);
    }
    return normalizer.normalize(raw, context);
  }

  private GeneratedCoachAdvice deloadAdvice(CoachContext context, String previous) {
    ObjectNode change = objectMapper.createObjectNode();
    change.put("action", "DELOAD");
    int sets = Math.max(0, context.maxSetsRemovable());
    if (sets > 0) {
      change.put("setsDelta", -sets);
    }
    if (context.weightBounds() != null) {
      BigDecimal delta =
          context.weightBounds().minDelta().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
      change.put("weightDelta", delta);
    }
    return new GeneratedCoachAdvice(
        RecommendationType.DELOAD,
        previous,
        change.toString(),
        "Performance declined across recent sessions while readiness shows elevated soreness or "
            + "stress. A short deload within safe bounds is recommended.",
        ConfidenceLevel.HIGH);
  }

  private GeneratedCoachAdvice reduceAdvice(CoachContext context, String previous) {
    ObjectNode change = objectMapper.createObjectNode();
    int maxRemove = context.maxSetsRemovable();
    RecommendationType type;
    String reasoning;
    if (maxRemove > 0
        && context.readiness() != null
        && context.readiness().soreness() != null
        && context.readiness().soreness() >= 3) {
      type = RecommendationType.VOLUME_ADJUSTMENT;
      change.put("setsDelta", -maxRemove);
      reasoning =
          "Readiness suggests elevated fatigue. Removing a small amount of volume stays within "
              + "the computed set-removal bound.";
    } else if (context.weightBounds() != null) {
      type = RecommendationType.WEIGHT_ADJUSTMENT;
      BigDecimal delta =
          context
              .weightBounds()
              .minDelta()
              .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
              .min(BigDecimal.ZERO);
      change.put("weightDelta", delta);
      reasoning =
          "Energy or recovery signals look low. A modest weight reduction within the ±10% bound "
              + "is proposed.";
    } else {
      return generalAdvice(previous);
    }
    return new GeneratedCoachAdvice(
        type, previous, change.toString(), reasoning, ConfidenceLevel.MEDIUM);
  }

  private GeneratedCoachAdvice progressAdvice(CoachContext context, String previous) {
    ObjectNode change = objectMapper.createObjectNode();
    BigDecimal delta =
        context
            .weightBounds()
            .maxDelta()
            .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
            .max(BigDecimal.ZERO);
    // Prefer a small progressive step when last weight is known.
    if (delta.compareTo(BigDecimal.ZERO) == 0) {
      delta = context.weightBounds().maxDelta();
    }
    change.put("weightDelta", delta);
    return new GeneratedCoachAdvice(
        RecommendationType.WEIGHT_ADJUSTMENT,
        previous,
        change.toString(),
        "Recent sessions look stable. A small weight increase within the ±10% bound is proposed.",
        ConfidenceLevel.MEDIUM);
  }

  private GeneratedCoachAdvice generalAdvice(String previous) {
    ObjectNode change = objectMapper.createObjectNode();
    change.put("action", "MAINTAIN");
    return new GeneratedCoachAdvice(
        RecommendationType.GENERAL,
        previous,
        change.toString(),
        "Not enough exercise history for a load change. Keep training consistent and log sets so "
            + "future recommendations can use tighter bounds.",
        ConfidenceLevel.LOW);
  }

  private static boolean shouldReduceLoad(CoachContext context) {
    if (context.readiness() == null) {
      return false;
    }
    var r = context.readiness();
    return (r.energyLevel() != null && r.energyLevel() <= 2)
        || (r.soreness() != null && r.soreness() >= 4)
        || (r.stress() != null && r.stress() >= 4)
        || (r.sleepQuality() != null && r.sleepQuality() <= 2);
  }
}
