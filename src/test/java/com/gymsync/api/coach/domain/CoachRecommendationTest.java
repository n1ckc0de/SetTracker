package com.gymsync.api.coach.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymsync.api.user.domain.Role;
import com.gymsync.api.user.domain.User;
import org.junit.jupiter.api.Test;

class CoachRecommendationTest {

  @Test
  void acceptModifyDismiss_onlyFromProposed() {
    User user = new User("coach@example.com", "hash", "Coach", Role.USER);
    CoachRecommendation rec =
        new CoachRecommendation(
            user,
            null,
            null,
            null,
            RecommendationType.GENERAL,
            "{}",
            "{\"action\":\"MAINTAIN\"}",
            "Steady",
            ConfidenceLevel.LOW);

    rec.accept();
    assertThat(rec.getStatus()).isEqualTo(RecommendationStatus.ACCEPTED);
    assertThat(rec.getRespondedAt()).isNotNull();
    assertThatThrownBy(rec::dismiss).isInstanceOf(IllegalStateException.class);

    CoachRecommendation rec2 =
        new CoachRecommendation(
            user,
            null,
            null,
            null,
            RecommendationType.WEIGHT_ADJUSTMENT,
            "{}",
            "{\"weightDelta\":5}",
            "Up",
            ConfidenceLevel.MEDIUM);
    rec2.modify("{\"weightDelta\":2}", "Tweaked");
    assertThat(rec2.getStatus()).isEqualTo(RecommendationStatus.MODIFIED);
    assertThat(rec2.getProposedChange()).isEqualTo("{\"weightDelta\":2}");
    assertThat(rec2.getReasoning()).isEqualTo("Tweaked");

    CoachRecommendation rec3 =
        new CoachRecommendation(
            user,
            null,
            null,
            null,
            RecommendationType.GENERAL,
            "{}",
            "{\"action\":\"MAINTAIN\"}",
            "Steady",
            ConfidenceLevel.LOW);
    rec3.dismiss();
    assertThat(rec3.getStatus()).isEqualTo(RecommendationStatus.DISMISSED);
  }
}
