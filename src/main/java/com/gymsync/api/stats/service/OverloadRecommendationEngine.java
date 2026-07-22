package com.gymsync.api.stats.service;

import java.math.BigDecimal;

/**
 * Abstraction over progressive-overload recommendation strategies, so a rule-based engine (the only
 * implementation for now) could later be replaced or supplemented by a smarter one (e.g.
 * LLM-assisted) without changing callers (spec section 16).
 */
public interface OverloadRecommendationEngine {

  /**
   * @param previousWeight the weight used in the most recent countable session for the exercise
   * @param previousReps the reps completed at that weight (the top set of that session)
   */
  RecommendationResult recommend(BigDecimal previousWeight, int previousReps);
}
