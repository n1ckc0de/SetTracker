package com.gymsync.api.stats.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymsync.api.stats.config.TrainingProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeterministicOverloadRecommendationEngineTest {

  private DeterministicOverloadRecommendationEngine engine;

  @BeforeEach
  void setUp() {
    TrainingProperties properties = new TrainingProperties();
    properties.setTargetRepMin(8);
    properties.setTargetRepMax(12);
    properties.setWeightIncrement(new BigDecimal("5.00"));
    engine = new DeterministicOverloadRecommendationEngine(properties);
  }

  @Test
  void recommend_repsAtUpperBound_recommendsWeightIncreaseWithLowerRepTarget() {
    RecommendationResult result = engine.recommend(new BigDecimal("135"), 12);

    assertThat(result.optionA().weight()).isEqualTo(new BigDecimal("140.00"));
    assertThat(result.optionA().targetReps()).isEqualTo(8);
    assertThat(result.optionB().weight()).isEqualTo(new BigDecimal("135"));
    assertThat(result.optionB().targetReps()).isEqualTo(13);
    assertThat(result.reason()).containsIgnoringCase("upper end");
  }

  @Test
  void recommend_repsAboveUpperBound_stillRecommendsIncrease() {
    RecommendationResult result = engine.recommend(new BigDecimal("100"), 15);

    assertThat(result.optionA().weight()).isEqualTo(new BigDecimal("105.00"));
    assertThat(result.optionA().targetReps()).isEqualTo(8);
  }

  @Test
  void recommend_repsBelowUpperBound_recommendsSameWeightWithMoreReps() {
    RecommendationResult result = engine.recommend(new BigDecimal("135"), 10);

    assertThat(result.optionA().weight()).isEqualTo(new BigDecimal("135"));
    assertThat(result.optionA().targetReps()).isEqualTo(11);
    assertThat(result.optionB().weight()).isEqualTo(new BigDecimal("135"));
    assertThat(result.optionB().targetReps()).isEqualTo(10);
    assertThat(result.reason()).containsIgnoringCase("haven't yet reached");
  }

  @Test
  void recommend_nextRepsNeverExceedsTargetMax_whenJustBelowBoundary() {
    RecommendationResult result = engine.recommend(new BigDecimal("135"), 11);

    assertThat(result.optionA().targetReps()).isEqualTo(12);
  }

  @Test
  void recommend_neverRecommendsNegativeWeight() {
    RecommendationResult result = engine.recommend(BigDecimal.ZERO, 12);

    assertThat(result.optionA().weight()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    assertThat(result.optionB().weight()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
  }

  @Test
  void recommend_weightIncreaseNeverExceedsConfiguredIncrement() {
    RecommendationResult result = engine.recommend(new BigDecimal("135"), 12);

    BigDecimal increase = result.optionA().weight().subtract(new BigDecimal("135"));
    assertThat(increase).isEqualTo(new BigDecimal("5.00"));
  }

  @Test
  void recommend_lowHistoryOfOneRep_stillProducesSensibleOptions() {
    RecommendationResult result = engine.recommend(new BigDecimal("45"), 1);

    assertThat(result.optionA().targetReps()).isEqualTo(2);
    assertThat(result.optionA().weight()).isEqualTo(new BigDecimal("45"));
  }
}
