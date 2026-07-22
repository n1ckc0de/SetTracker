package com.gymsync.api.strength.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymsync.api.strength.service.Estimated1RmService.EstimateRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class Estimated1RmServiceTest {

  private final Estimated1RmService service = new Estimated1RmService();

  @Test
  void epley_formula_matchesStaticHelper() {
    BigDecimal weight = new BigDecimal("100");
    assertThat(Estimated1RmService.epley(weight, 5)).isEqualByComparingTo("116.67");
    assertThat(service.estimate(new EstimateRequest(weight, 5, null)).epley())
        .isEqualByComparingTo("116.67");
  }

  @Test
  void brzycki_formula_matchesStaticHelper() {
    BigDecimal weight = new BigDecimal("100");
    assertThat(Estimated1RmService.brzycki(weight, 5)).isEqualByComparingTo("112.50");
    assertThat(service.estimate(new EstimateRequest(weight, 5, null)).brzycki())
        .isEqualByComparingTo("112.50");
  }

  @Test
  void workingEstimate_isAverageOfEpleyAndBrzycki() {
    var response = service.estimate(new EstimateRequest(new BigDecimal("100"), 5, 2));
    assertThat(response.workingEstimate()).isEqualByComparingTo("114.59");
    assertThat(response.rangeMin()).isEqualByComparingTo("112.50");
    assertThat(response.rangeMax()).isEqualByComparingTo("116.67");
    assertThat(response.rirContext()).isEqualTo(2);
    assertThat(response.lowConfidence()).isFalse();
  }

  @Test
  void repsAboveTwelve_setsLowConfidence() {
    var response = service.estimate(new EstimateRequest(new BigDecimal("135"), 13, null));
    assertThat(response.lowConfidence()).isTrue();
    assertThat(response.workingEstimate()).isGreaterThan(new BigDecimal("135"));
  }

  @Test
  void rejectsInvalidInput() {
    assertThatThrownBy(() -> service.estimate(new EstimateRequest(new BigDecimal("-1"), 5, null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.estimate(new EstimateRequest(new BigDecimal("100"), 0, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
