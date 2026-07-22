package com.gymsync.api.coach.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gymsync.api.coach.bounds.CoachBoundsCalculator;
import com.gymsync.api.coach.domain.ConfidenceLevel;
import com.gymsync.api.coach.domain.RecommendationType;
import com.gymsync.api.common.exception.BadRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Validates and clamps generator output so out-of-bounds or malformed LLM responses never reach
 * persistence raw. Bounds come from {@link CoachBoundsCalculator}; this class only enforces them.
 */
@Component
public class CoachAdviceNormalizer {

  public static final String SAFETY_REASONING =
      "Your note mentions pain, injury, or similar concerns. Pause training, rest, and consult a "
          + "qualified medical professional before continuing. GymSync will not suggest loading "
          + "changes while a safety hold is active.";

  private final ObjectMapper objectMapper;

  public CoachAdviceNormalizer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public GeneratedCoachAdvice safetyHoldAdvice(String previousPerformanceJson) {
    ObjectNode change = objectMapper.createObjectNode();
    change.put("action", "REST");
    change.put("seekProfessional", true);
    return new GeneratedCoachAdvice(
        RecommendationType.SAFETY_HOLD,
        previousPerformanceJson == null || previousPerformanceJson.isBlank()
            ? "{\"sessions\":[]}"
            : previousPerformanceJson,
        change.toString(),
        SAFETY_REASONING,
        ConfidenceLevel.HIGH);
  }

  /**
   * Parses and clamps proposedChange JSON. Rejects unparseable payloads; clamps numeric deltas into
   * context bounds.
   */
  public GeneratedCoachAdvice normalize(GeneratedCoachAdvice raw, CoachContext context) {
    if (raw == null) {
      throw new BadRequestException("Coach generator returned no advice.");
    }
    if (raw.type() == null || raw.reasoning() == null || raw.reasoning().isBlank()) {
      throw new BadRequestException("Coach advice missing type or reasoning.");
    }
    if (raw.confidence() == null) {
      throw new BadRequestException("Coach advice missing confidence.");
    }
    if (raw.type() == RecommendationType.SAFETY_HOLD) {
      return safetyHoldAdvice(raw.previousPerformanceJson());
    }

    String previous =
        raw.previousPerformanceJson() == null || raw.previousPerformanceJson().isBlank()
            ? buildPreviousPerformance(context)
            : raw.previousPerformanceJson();

    String clampedChange = clampProposedChange(raw.proposedChangeJson(), raw.type(), context);
    String reasoning = raw.reasoning().trim();
    if (reasoning.length() > 2000) {
      reasoning = reasoning.substring(0, 2000);
    }
    return new GeneratedCoachAdvice(
        raw.type(), previous, clampedChange, reasoning, raw.confidence());
  }

  public String buildPreviousPerformance(CoachContext context) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("exerciseName", context.exerciseName());
    var sessions = objectMapper.createArrayNode();
    if (context.recentSessionTopWeights() != null) {
      for (BigDecimal w : context.recentSessionTopWeights()) {
        sessions.add(w);
      }
    }
    root.set("sessionTopWeights", sessions);
    if (context.recentSetCount() != null) {
      root.put("recentSetCount", context.recentSetCount());
    }
    return root.toString();
  }

  String clampProposedChange(
      String proposedChangeJson, RecommendationType type, CoachContext context) {
    if (proposedChangeJson == null || proposedChangeJson.isBlank()) {
      throw new BadRequestException("Coach advice missing proposedChange.");
    }
    JsonNode node;
    try {
      node = objectMapper.readTree(proposedChangeJson);
    } catch (Exception ex) {
      throw new BadRequestException("Malformed coach proposedChange JSON.");
    }
    if (!node.isObject()) {
      throw new BadRequestException("Coach proposedChange must be a JSON object.");
    }
    ObjectNode out = objectMapper.createObjectNode();
    if (node.has("action") && node.get("action").isTextual()) {
      out.put("action", node.get("action").asText());
    }
    String action = out.path("action").asText("");
    boolean estimate1Rm = "ESTIMATE_1RM".equals(action);

    if (estimate1Rm) {
      copyDecimalField(node, out, "inputWeight");
      copyIntField(node, out, "inputReps");
      copyDecimalField(node, out, "workingEstimate");
      copyDecimalField(node, out, "rangeMin");
      copyDecimalField(node, out, "rangeMax");
      copyDecimalField(node, out, "epley");
      copyDecimalField(node, out, "brzycki");
      if (node.has("lowConfidence")) {
        out.put("lowConfidence", node.get("lowConfidence").asBoolean());
      }
      return out.toString();
    }

    if (node.has("weightDelta")) {
      BigDecimal delta = decimalOrThrow(node.get("weightDelta"), "weightDelta");
      if (context.weightBounds() != null) {
        delta = delta.max(context.weightBounds().minDelta()).min(context.weightBounds().maxDelta());
      } else {
        delta = BigDecimal.ZERO;
      }
      out.put("weightDelta", delta.setScale(2, RoundingMode.HALF_UP));
    }

    if (node.has("proposedWeight") && context.weightBounds() != null) {
      BigDecimal proposed = decimalOrThrow(node.get("proposedWeight"), "proposedWeight");
      proposed =
          proposed
              .max(context.weightBounds().minWeight())
              .min(context.weightBounds().maxWeight())
              .setScale(2, RoundingMode.HALF_UP);
      out.put("proposedWeight", proposed);
    } else if (node.has("proposedWeight")) {
      out.put("proposedWeight", decimalOrThrow(node.get("proposedWeight"), "proposedWeight"));
    }

    copyTextField(node, out, "readinessContextSummary");
    copyTextField(node, out, "performanceContextSummary");
    copyIntField(node, out, "proposedSets");
    copyIntField(node, out, "proposedRepRangeMin");
    copyIntField(node, out, "proposedRepRangeMax");
    copyIntField(node, out, "targetRir");

    if (node.has("setsDelta")) {
      int setsDelta = intOrThrow(node.get("setsDelta"), "setsDelta");
      int maxRemove = Math.max(0, context.maxSetsRemovable());
      // Only allow removing sets within bounds (negative or zero).
      setsDelta = Math.max(-maxRemove, Math.min(0, setsDelta));
      out.put("setsDelta", setsDelta);
    }

    if (type == RecommendationType.DELOAD && !out.has("action")) {
      out.put("action", "DELOAD");
    }
    if (type == RecommendationType.GENERAL && out.isEmpty()) {
      out.put("action", "MAINTAIN");
    }
    if (out.isEmpty()) {
      throw new BadRequestException("Coach proposedChange had no usable fields.");
    }
    return out.toString();
  }

  private static BigDecimal decimalOrThrow(JsonNode node, String field) {
    if (node == null || !node.isNumber()) {
      throw new BadRequestException("Coach proposedChange." + field + " must be numeric.");
    }
    return node.decimalValue();
  }

  private static int intOrThrow(JsonNode node, String field) {
    if (node == null || !node.isIntegralNumber()) {
      throw new BadRequestException("Coach proposedChange." + field + " must be an integer.");
    }
    return node.asInt();
  }

  private static void copyTextField(JsonNode in, ObjectNode out, String field) {
    if (in.has(field) && in.get(field).isTextual()) {
      out.put(field, in.get(field).asText());
    }
  }

  private static void copyIntField(JsonNode in, ObjectNode out, String field) {
    if (in.has(field) && in.get(field).isIntegralNumber()) {
      out.put(field, in.get(field).asInt());
    }
  }

  private static void copyDecimalField(JsonNode in, ObjectNode out, String field) {
    if (in.has(field) && in.get(field).isNumber()) {
      out.put(field, in.get(field).decimalValue());
    }
  }
}
