package com.gymsync.api.exercise.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CuratedYouTubeDemoCatalogTest {

  @Test
  void findByExerciseName_matchesSeededNamesCaseInsensitively() {
    assertThat(CuratedYouTubeDemoCatalog.findByExerciseName("Bench Press"))
        .isPresent()
        .get()
        .extracting(CuratedYouTubeDemoCatalog.CuratedDemo::demoVideoUrl)
        .asString()
        .contains("youtube.com/watch?v=");

    assertThat(CuratedYouTubeDemoCatalog.findByExerciseName("romanian deadlift")).isPresent();
    assertThat(CuratedYouTubeDemoCatalog.findByExerciseName("Totally Made Up Move")).isEmpty();
  }
}
