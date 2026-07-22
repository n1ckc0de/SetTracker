package com.gymsync.api.stats.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code gymsync.training.*} configuration used by the deterministic progressive-overload
 * recommendation engine. See docs/DECISIONS.md for why these are configurable rather than hardcoded
 * constants.
 */
@ConfigurationProperties("gymsync.training")
@Validated
public class TrainingProperties {

  @Positive private int targetRepMin = 8;

  @Positive private int targetRepMax = 12;

  @NotNull @Positive private BigDecimal weightIncrement = new BigDecimal("5.00");

  public int getTargetRepMin() {
    return targetRepMin;
  }

  public void setTargetRepMin(int targetRepMin) {
    this.targetRepMin = targetRepMin;
  }

  public int getTargetRepMax() {
    return targetRepMax;
  }

  public void setTargetRepMax(int targetRepMax) {
    this.targetRepMax = targetRepMax;
  }

  public BigDecimal getWeightIncrement() {
    return weightIncrement;
  }

  public void setWeightIncrement(BigDecimal weightIncrement) {
    this.weightIncrement = weightIncrement;
  }
}
