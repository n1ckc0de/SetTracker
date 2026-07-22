package com.gymsync.api.coach.llm;

import com.gymsync.api.coach.domain.ConfidenceLevel;
import com.gymsync.api.coach.domain.RecommendationType;

/** Structured generator output before persistence / clamping. */
public record GeneratedCoachAdvice(
    RecommendationType type,
    String previousPerformanceJson,
    String proposedChangeJson,
    String reasoning,
    ConfidenceLevel confidence) {}
