package com.gymsync.api.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gymsync.api.common.exception.NotFoundException;
import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.exercise.domain.ExerciseCategory;
import com.gymsync.api.exercise.repository.ExerciseRepository;
import com.gymsync.api.stats.dto.PersonalRecordResponse;
import com.gymsync.api.stats.dto.WeeklyStatsResponse;
import com.gymsync.api.stats.repository.StatsQueryRepository;
import com.gymsync.api.user.domain.Role;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import com.gymsync.api.workout.domain.ImportStatus;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.domain.WorkoutSet;
import com.gymsync.api.workout.domain.WorkoutSource;
import com.gymsync.api.workout.domain.WorkoutStatus;
import com.gymsync.api.workout.repository.WorkoutRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StatsServiceTest {

  private StatsQueryRepository statsQueryRepository;
  private WorkoutRepository workoutRepository;
  private UserRepository userRepository;
  private ExerciseRepository exerciseRepository;
  private OverloadRecommendationEngine recommendationEngine;
  private StatsService statsService;

  private User user;
  private UUID userId;

  @BeforeEach
  void setUp() {
    statsQueryRepository = mock(StatsQueryRepository.class);
    workoutRepository = mock(WorkoutRepository.class);
    userRepository = mock(UserRepository.class);
    exerciseRepository = mock(ExerciseRepository.class);
    recommendationEngine =
        new DeterministicOverloadRecommendationEngine(
            new com.gymsync.api.stats.config.TrainingProperties());
    statsService =
        new StatsService(
            statsQueryRepository,
            workoutRepository,
            userRepository,
            exerciseRepository,
            recommendationEngine);

    user = new User("stats@example.com", "hash", "Stats", Role.USER);
    userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);
    when(userRepository.getReferenceById(userId)).thenReturn(user);
  }

  @Test
  void weeklyStats_aggregatesVolumeAndComparesToPriorWeek() {
    LocalDate monday = LocalDate.of(2026, 7, 13);
    Exercise bench = exercise("Bench Press", ExerciseCategory.CHEST);
    Exercise run = exercise("Running", ExerciseCategory.CARDIO);

    Workout workout = completedWorkout();
    WorkoutSet heavy = set(workout, bench, "135.00", 10, 1);
    WorkoutSet cardio = set(workout, run, "0.00", 30, 1);

    when(statsQueryRepository.findCountableSetsBetween(eq(userId), any(), any()))
        .thenReturn(List.of(heavy, cardio)) // current week
        .thenReturn(List.of(set(workout, bench, "100.00", 8, 1))); // prior week
    when(workoutRepository.countCountableWorkouts(eq(user), any(), any())).thenReturn(1L);

    WeeklyStatsResponse response = statsService.weeklyStats(userId, monday);

    assertThat(response.completedWorkouts()).isEqualTo(1L);
    assertThat(response.totalSets()).isEqualTo(2L);
    assertThat(response.totalReps()).isEqualTo(40L);
    // volume = 135*10 + 0*30 = 1350; bodyweight/cardio with weight 0 contributes zero volume
    assertThat(response.totalVolume()).isEqualByComparingTo("1350.00");
    assertThat(response.volumeByExercise()).hasSize(2);
    assertThat(response.volumeByCategory())
        .extracting(c -> c.category().name())
        .contains("CHEST", "CARDIO");
    assertThat(response.comparisonWithPriorWeek().priorWeekVolume()).isEqualByComparingTo("800.00");
    assertThat(response.comparisonWithPriorWeek().volumeChange()).isEqualByComparingTo("550.00");
  }

  @Test
  void personalRecord_usesEpleyEstimateAndHeaviestWeight() {
    UUID exerciseId = UUID.randomUUID();
    Exercise exercise = exercise("Squat", ExerciseCategory.LEGS);
    ReflectionTestUtils.setField(exercise, "id", exerciseId);
    when(exerciseRepository.findById(exerciseId)).thenReturn(Optional.of(exercise));

    Workout workout = completedWorkout();
    WorkoutSet lighterHighReps = set(workout, exercise, "100.00", 12, 1);
    WorkoutSet heaviest = set(workout, exercise, "185.00", 5, 2);
    when(statsQueryRepository.findCountableSetsForExercise(userId, exerciseId))
        .thenReturn(List.of(lighterHighReps, heaviest));

    PersonalRecordResponse pr = statsService.personalRecord(userId, exerciseId);

    assertThat(pr.heaviestWeight()).isEqualByComparingTo("185.00");
    assertThat(pr.repsAtHeaviestWeight()).isEqualTo(5);
    assertThat(pr.highestRepsAtHeaviestWeight()).isEqualTo(5);
    // Epley: 185 * (1 + 5/30) = 215.83
    assertThat(pr.estimatedOneRepMax()).isEqualByComparingTo("215.83");
    assertThat(pr.disclaimer()).contains("informational");
  }

  @Test
  void personalRecord_whenNoHistory_throwsNotFound() {
    UUID exerciseId = UUID.randomUUID();
    Exercise exercise = exercise("Deadlift", ExerciseCategory.BACK);
    ReflectionTestUtils.setField(exercise, "id", exerciseId);
    when(exerciseRepository.findById(exerciseId)).thenReturn(Optional.of(exercise));
    when(statsQueryRepository.findCountableSetsForExercise(userId, exerciseId))
        .thenReturn(List.of());

    assertThatThrownBy(() -> statsService.personalRecord(userId, exerciseId))
        .isInstanceOf(NotFoundException.class);
  }

  private Exercise exercise(String name, ExerciseCategory category) {
    Exercise exercise = new Exercise(name, name.toLowerCase(), category, null);
    ReflectionTestUtils.setField(exercise, "id", UUID.randomUUID());
    return exercise;
  }

  private Workout completedWorkout() {
    Workout workout =
        new Workout(
            user,
            Instant.parse("2026-07-14T18:00:00Z"),
            WorkoutStatus.COMPLETED,
            WorkoutSource.MANUAL,
            ImportStatus.NOT_APPLICABLE);
    ReflectionTestUtils.setField(workout, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(workout, "endedAt", Instant.parse("2026-07-14T19:00:00Z"));
    return workout;
  }

  private WorkoutSet set(
      Workout workout, Exercise exercise, String weight, int reps, int setNumber) {
    WorkoutSet workoutSet =
        new WorkoutSet(
            workout,
            exercise,
            new BigDecimal(weight),
            reps,
            setNumber,
            Instant.parse("2026-07-14T18:30:00Z"));
    ReflectionTestUtils.setField(workoutSet, "id", UUID.randomUUID());
    return workoutSet;
  }
}
