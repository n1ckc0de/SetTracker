package com.gymsync.api.strength.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/** Deterministic estimated-1RM calculations — no LLM involvement. */
@Service
public class Estimated1RmService {

  public static final int LOW_CONFIDENCE_REP_THRESHOLD = 12;

  public record EstimateRequest(BigDecimal weight, int reps, Integer rir) {}

  public record EstimateResponse(
      BigDecimal epley,
      BigDecimal brzycki,
      BigDecimal workingEstimate,
      BigDecimal rangeMin,
      BigDecimal rangeMax,
      boolean lowConfidence,
      Integer rirContext) {}

  public EstimateResponse estimate(EstimateRequest request) {
    if (request.weight() == null || request.weight().compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("weight must be non-negative");
    }
    if (request.reps() <= 0) {
      throw new IllegalArgumentException("reps must be positive");
    }
    BigDecimal epley = epley(request.weight(), request.reps());
    BigDecimal brzycki = brzycki(request.weight(), request.reps());
    BigDecimal working = epley.add(brzycki).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    BigDecimal min = epley.min(brzycki);
    BigDecimal max = epley.max(brzycki);
    boolean lowConfidence = request.reps() > LOW_CONFIDENCE_REP_THRESHOLD;
    return new EstimateResponse(epley, brzycki, working, min, max, lowConfidence, request.rir());
  }

  public static BigDecimal epley(BigDecimal weight, int reps) {
    return weight
        .multiply(
            BigDecimal.ONE.add(
                BigDecimal.valueOf(reps).divide(BigDecimal.valueOf(30), 6, RoundingMode.HALF_UP)))
        .setScale(2, RoundingMode.HALF_UP);
  }

  public static BigDecimal brzycki(BigDecimal weight, int reps) {
    if (reps >= 37) {
      return weight.setScale(2, RoundingMode.HALF_UP);
    }
    return weight
        .multiply(BigDecimal.valueOf(36))
        .divide(BigDecimal.valueOf(37 - reps), 2, RoundingMode.HALF_UP);
  }
}
