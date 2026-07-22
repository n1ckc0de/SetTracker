package com.gymsync.api.exercise.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymsync.api.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class ExerciseMediaValidatorTest {

  @Test
  void acceptsValidHttpUrlsWithoutAttributionWhenNoDemo() {
    assertThatCode(
            () -> ExerciseMediaValidator.validate("https://example.com/thumb.jpg", null, null))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsMalformedUrl() {
    assertThatThrownBy(() -> ExerciseMediaValidator.validate("not-a-url", null, null))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("thumbnailUrl");
  }

  @Test
  void requiresAttributionWhenDemoVideoPresent() {
    assertThatThrownBy(
            () -> ExerciseMediaValidator.validate(null, "https://example.com/demo", null))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("mediaAttribution");
  }

  @Test
  void acceptsDemoWithAttribution() {
    assertThatCode(
            () ->
                ExerciseMediaValidator.validate(
                    null, "https://example.com/demo", "Demo via Example"))
        .doesNotThrowAnyException();
  }
}
