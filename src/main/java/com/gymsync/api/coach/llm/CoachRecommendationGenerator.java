package com.gymsync.api.coach.llm;

public interface CoachRecommendationGenerator {
  GeneratedCoachAdvice generate(CoachContext context);
}
