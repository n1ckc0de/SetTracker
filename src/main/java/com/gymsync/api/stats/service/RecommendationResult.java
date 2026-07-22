package com.gymsync.api.stats.service;

import com.gymsync.api.stats.dto.RecommendationOption;

public record RecommendationResult(
    RecommendationOption optionA, RecommendationOption optionB, String reason) {}
