package com.gymsync.api.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExerciseNameNormalizerTest {

  @Test
  void normalize_lowercasesAndTrims() {
    assertThat(ExerciseNameNormalizer.normalize("  Bench Press  ")).isEqualTo("bench press");
  }

  @Test
  void normalize_collapsesInternalWhitespace() {
    assertThat(ExerciseNameNormalizer.normalize("Bench   Press")).isEqualTo("bench press");
  }

  @Test
  void normalize_stripsPunctuationButKeepsHyphens() {
    assertThat(ExerciseNameNormalizer.normalize("Pull-Up!!")).isEqualTo("pull-up");
    assertThat(ExerciseNameNormalizer.normalize("Bench Press (Barbell)"))
        .isEqualTo("bench press barbell");
  }

  @Test
  void normalize_matchesSeedDataConventions() {
    assertThat(ExerciseNameNormalizer.normalize("Overhead Press")).isEqualTo("overhead press");
    assertThat(ExerciseNameNormalizer.normalize("Lat Pulldown")).isEqualTo("lat pulldown");
  }

  @Test
  void normalize_nullReturnsEmptyString() {
    assertThat(ExerciseNameNormalizer.normalize(null)).isEmpty();
  }
}
