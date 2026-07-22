package com.gymsync.api.strength.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.exercise.domain.ExerciseCategory;
import com.gymsync.api.stats.repository.StatsQueryRepository;
import com.gymsync.api.user.domain.Role;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.workout.domain.ImportStatus;
import com.gymsync.api.workout.domain.SetType;
import com.gymsync.api.workout.domain.WeightEntryMode;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.domain.WorkoutSet;
import com.gymsync.api.workout.domain.WorkoutSource;
import com.gymsync.api.workout.domain.WorkoutStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PrDetectionServiceTest {

  @Mock private StatsQueryRepository statsQueryRepository;

  private PrDetectionService prDetectionService;
  private UUID userId;
  private UUID exerciseId;
  private User user;
  private Workout workout;
  private Exercise exercise;

  @BeforeEach
  void setUp() {
    prDetectionService = new PrDetectionService(statsQueryRepository, new Estimated1RmService());
    userId = UUID.randomUUID();
    exerciseId = UUID.randomUUID();
    user = new User("pr@example.com", "hash", "PR", Role.USER);
    workout =
        new Workout(
            user,
            Instant.parse("2026-07-18T12:00:00Z"),
            WorkoutStatus.COMPLETED,
            WorkoutSource.MANUAL,
            ImportStatus.NOT_APPLICABLE);
    exercise = new Exercise("Squat", "squat", ExerciseCategory.LEGS, null);
    ReflectionTestUtils.setField(exercise, "id", exerciseId);
  }

  @Test
  void warmupSet_neverCountsAsPr() {
    WorkoutSet warmup = workingSet("200", 5, SetType.WARMUP);

    var result = prDetectionService.detect(userId, warmup, null);

    assertThat(result.weightPr()).isFalse();
    assertThat(result.estimatedOneRepMaxPr()).isFalse();
  }

  @Test
  void highRepSet_mayHaveWeightPr_butNeverE1rmPr() {
    WorkoutSet prior = workingSet("180", 5, SetType.WORKING);
    WorkoutSet candidate = workingSet("185", 15, SetType.WORKING);
    when(statsQueryRepository.findCountableSetsForExercise(eq(userId), eq(exerciseId)))
        .thenReturn(List.of(prior));

    var result = prDetectionService.detect(userId, candidate, null);

    assertThat(result.weightPr()).isTrue();
    assertThat(result.estimatedOneRepMaxPr()).isFalse();
  }

  @Test
  void perSideEntry_usesNormalizedTotalWeightForComparison() {
    WorkoutSet prior =
        new WorkoutSet(
            workout,
            exercise,
            new BigDecimal("145"),
            5,
            1,
            Instant.parse("2026-07-18T12:00:00Z"),
            SetType.WORKING,
            null,
            WeightEntryMode.PER_SIDE,
            new BigDecimal("45"),
            new BigDecimal("50"),
            null,
            true);
    ReflectionTestUtils.setField(prior, "id", UUID.randomUUID());

    WorkoutSet candidate =
        new WorkoutSet(
            workout,
            exercise,
            new BigDecimal("155"),
            5,
            2,
            Instant.parse("2026-07-18T12:05:00Z"),
            SetType.WORKING,
            null,
            WeightEntryMode.PER_SIDE,
            new BigDecimal("45"),
            new BigDecimal("55"),
            null,
            true);
    ReflectionTestUtils.setField(candidate, "id", UUID.randomUUID());

    when(statsQueryRepository.findCountableSetsForExercise(eq(userId), eq(exerciseId)))
        .thenReturn(List.of(prior));

    var result = prDetectionService.detect(userId, candidate, null);

    assertThat(candidate.getWeight()).isEqualByComparingTo("155.00");
    assertThat(result.weightPr()).isTrue();
  }

  @Test
  void e1rmPr_whenWorkingEstimateBeatsHistory() {
    WorkoutSet prior = workingSet("100", 5, SetType.WORKING);
    WorkoutSet candidate = workingSet("105", 5, SetType.WORKING);
    when(statsQueryRepository.findCountableSetsForExercise(eq(userId), eq(exerciseId)))
        .thenReturn(List.of(prior));

    var result = prDetectionService.detect(userId, candidate, null);

    assertThat(result.weightPr()).isTrue();
    assertThat(result.estimatedOneRepMaxPr()).isTrue();
  }

  private WorkoutSet workingSet(String normalizedWeight, int reps, SetType setType) {
    WorkoutSet set =
        new WorkoutSet(
            workout,
            exercise,
            new BigDecimal(normalizedWeight),
            reps,
            1,
            Instant.parse("2026-07-18T12:00:00Z"),
            setType,
            null,
            WeightEntryMode.TOTAL,
            null,
            new BigDecimal(normalizedWeight),
            null,
            true);
    ReflectionTestUtils.setField(set, "id", UUID.randomUUID());
    return set;
  }
}
