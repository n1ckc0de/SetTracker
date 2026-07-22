package com.gymsync.api.strength.dto;

import com.gymsync.api.strength.service.Estimated1RmService.EstimateResponse;
import java.math.BigDecimal;

public record Estimate1RmResponse(
    String exerciseName,
    BigDecimal epley,
    BigDecimal brzycki,
    BigDecimal workingEstimate,
    BigDecimal rangeMin,
    BigDecimal rangeMax,
    boolean lowConfidence,
    Integer rirContext,
    String confidenceNote) {

  public static Estimate1RmResponse from(EstimateResponse result, String exerciseName) {
    String note =
        result.lowConfidence()
            ? "Rep count above 12 — estimate shown but confidence is low."
            : "Estimate based on Epley and Brzycki averages.";
    return new Estimate1RmResponse(
        exerciseName,
        result.epley(),
        result.brzycki(),
        result.workingEstimate(),
        result.rangeMin(),
        result.rangeMax(),
        result.lowConfidence(),
        result.rirContext(),
        note);
  }
}
