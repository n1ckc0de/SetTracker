package com.gymsync.api.coach.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymsync.api.readiness.dto.CreateReadinessRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ChatReadinessExtractorTest {

  @Test
  void extractsSignalsFromFreeText() {
    CreateReadinessRequest req =
        ChatReadinessExtractor.extract("didn't sleep well, very sore and stressed", null);
    assertThat(req.sleepQuality()).isEqualTo(2);
    assertThat(req.soreness()).isEqualTo(5);
    assertThat(req.stress()).isEqualTo(4);
    assertThat(req.note()).contains("didn't sleep");
  }

  @Test
  void extractsSleepHours() {
    CreateReadinessRequest req = ChatReadinessExtractor.extract("slept 6 hours last night", null);
    assertThat(req.sleepHours()).isEqualByComparingTo(new BigDecimal("6"));
  }

  @Test
  void blankMessage_returnsEmptySignals() {
    CreateReadinessRequest req = ChatReadinessExtractor.extract("   ", null);
    assertThat(req.note()).isNull();
    assertThat(req.energyLevel()).isNull();
  }
}
