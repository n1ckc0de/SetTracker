package com.gymsync.api.workout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.exercise.domain.ExerciseCategory;
import com.gymsync.api.user.domain.Role;
import com.gymsync.api.user.domain.User;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SetWeightNormalizerTest {

  @Test
  void totalMode_returnsEnteredWeightScaled() {
    BigDecimal total =
        SetWeightNormalizer.computeTotalWeight(
            new BigDecimal("135.555"), WeightEntryMode.TOTAL, null);
    assertThat(total).isEqualByComparingTo("135.56");
  }

  @Test
  void perSideMode_addsBarAndBothSides() {
    BigDecimal total =
        SetWeightNormalizer.computeTotalWeight(
            new BigDecimal("50"), WeightEntryMode.PER_SIDE, new BigDecimal("45"));
    assertThat(total).isEqualByComparingTo("145.00");
  }

  @Test
  void perSideMode_nullBarTreatedAsZero() {
    BigDecimal total =
        SetWeightNormalizer.computeTotalWeight(
            new BigDecimal("25.5"), WeightEntryMode.PER_SIDE, null);
    assertThat(total).isEqualByComparingTo("51.00");
  }

  @Test
  void workoutSet_roundTripMatchesNormalizer() {
    User user = new User("lifter@example.com", "hash", "Lifter", Role.USER);
    Workout workout =
        new Workout(
            user,
            Instant.parse("2026-07-18T12:00:00Z"),
            WorkoutStatus.ACTIVE,
            WorkoutSource.MANUAL,
            ImportStatus.NOT_APPLICABLE);
    Exercise exercise = new Exercise("Bench Press", "bench press", ExerciseCategory.CHEST, null);

    WorkoutSet totalSet =
        new WorkoutSet(
            workout,
            exercise,
            new BigDecimal("100"),
            5,
            1,
            Instant.parse("2026-07-18T12:05:00Z"),
            SetType.WORKING,
            null,
            WeightEntryMode.TOTAL,
            null,
            new BigDecimal("100"),
            null,
            true);
    assertThat(totalSet.getWeight()).isEqualByComparingTo("100.00");
    assertThat(
            SetWeightNormalizer.computeTotalWeight(
                totalSet.getEnteredWeight(),
                totalSet.getWeightEntryMode(),
                totalSet.getBarWeight()))
        .isEqualByComparingTo(totalSet.getWeight());

    WorkoutSet perSideSet =
        new WorkoutSet(
            workout,
            exercise,
            new BigDecimal("145"),
            5,
            2,
            Instant.parse("2026-07-18T12:06:00Z"),
            SetType.WORKING,
            null,
            WeightEntryMode.PER_SIDE,
            new BigDecimal("45"),
            new BigDecimal("50"),
            null,
            true);
    assertThat(perSideSet.getEnteredWeight()).isEqualByComparingTo("50");
    assertThat(perSideSet.getWeight()).isEqualByComparingTo("145.00");
    assertThat(
            SetWeightNormalizer.computeTotalWeight(
                perSideSet.getEnteredWeight(),
                perSideSet.getWeightEntryMode(),
                perSideSet.getBarWeight()))
        .isEqualByComparingTo(perSideSet.getWeight());
  }

  @Test
  void rejectsNegativeEnteredWeight() {
    assertThatThrownBy(
            () ->
                SetWeightNormalizer.computeTotalWeight(
                    new BigDecimal("-1"), WeightEntryMode.TOTAL, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
