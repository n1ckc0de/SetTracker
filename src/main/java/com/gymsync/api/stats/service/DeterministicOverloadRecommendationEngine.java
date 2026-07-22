package com.gymsync.api.stats.service;

import com.gymsync.api.stats.config.TrainingProperties;
import com.gymsync.api.stats.dto.RecommendationOption;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Simple, explainable business-rule engine for progressive-overload suggestions (spec section 16).
 * Deliberately not backed by an LLM: the recommendation must be reproducible, auditable, and safe
 * (never a negative weight, never an increment larger than the configured {@code
 * gymsync.training.weight-increment}).
 *
 * <p>Rule:
 *
 * <ul>
 *   <li>If the previous top set met or exceeded the configured upper rep target ({@code
 *       gymsync.training.target-rep-max}), recommend either increasing weight by one configured
 *       increment and resetting to the lower rep target ({@code targetRepMin}), or holding the
 *       weight and adding one more rep.
 *   <li>Otherwise, recommend holding the weight and adding one rep (capped at {@code
 *       targetRepMax}), or repeating the same weight/reps to build consistency first.
 * </ul>
 */
@Component
public class DeterministicOverloadRecommendationEngine implements OverloadRecommendationEngine {

  private final TrainingProperties properties;

  public DeterministicOverloadRecommendationEngine(TrainingProperties properties) {
    this.properties = properties;
  }

  @Override
  public RecommendationResult recommend(BigDecimal previousWeight, int previousReps) {
    int targetMin = properties.getTargetRepMin();
    int targetMax = properties.getTargetRepMax();
    BigDecimal increment = properties.getWeightIncrement();

    if (previousReps >= targetMax) {
      RecommendationOption optionA =
          new RecommendationOption(previousWeight.add(increment), targetMin);
      RecommendationOption optionB = new RecommendationOption(previousWeight, previousReps + 1);
      String reason =
          "You reached the upper end of your configured repetition range ("
              + targetMax
              + " reps). Option A increases weight by "
              + increment
              + " and resets to "
              + targetMin
              + " target reps; Option B keeps the same weight and adds one more rep.";
      return new RecommendationResult(optionA, optionB, reason);
    }

    int nextReps = Math.min(previousReps + 1, targetMax);
    RecommendationOption optionA = new RecommendationOption(previousWeight, nextReps);
    RecommendationOption optionB = new RecommendationOption(previousWeight, previousReps);
    String reason =
        "You haven't yet reached the upper end of your configured repetition range ("
            + targetMax
            + " reps). Option A keeps the same weight and adds one rep; Option B repeats the same"
            + " weight and reps to build consistency before increasing difficulty.";
    return new RecommendationResult(optionA, optionB, reason);
  }
}
