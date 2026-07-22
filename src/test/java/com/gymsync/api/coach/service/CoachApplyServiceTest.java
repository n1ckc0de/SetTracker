package com.gymsync.api.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsync.api.coach.domain.CoachRecommendation;
import com.gymsync.api.coach.domain.ConfidenceLevel;
import com.gymsync.api.coach.domain.RecommendationType;
import com.gymsync.api.coach.dto.ApplyCoachRecommendationRequest;
import com.gymsync.api.coach.repository.CoachRecommendationRepository;
import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.exercise.domain.ExerciseCategory;
import com.gymsync.api.user.domain.Role;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.workout.domain.ImportStatus;
import com.gymsync.api.workout.domain.SetType;
import com.gymsync.api.workout.domain.WeightEntryMode;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.domain.WorkoutSet;
import com.gymsync.api.workout.domain.WorkoutSource;
import com.gymsync.api.workout.domain.WorkoutStatus;
import com.gymsync.api.workout.repository.WorkoutRepository;
import com.gymsync.api.workout.repository.WorkoutSetRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CoachApplyServiceTest {

  @Mock private CoachRecommendationRepository coachRecommendationRepository;
  @Mock private WorkoutRepository workoutRepository;
  @Mock private WorkoutSetRepository workoutSetRepository;

  private CoachApplyService coachApplyService;
  private UUID userId;
  private UUID workoutId;
  private UUID exerciseId;
  private UUID recommendationId;
  private User user;
  private Workout workout;
  private Exercise exercise;

  @BeforeEach
  void setUp() {
    coachApplyService =
        new CoachApplyService(
            coachRecommendationRepository,
            workoutRepository,
            workoutSetRepository,
            new ObjectMapper());
    userId = UUID.randomUUID();
    workoutId = UUID.randomUUID();
    exerciseId = UUID.randomUUID();
    recommendationId = UUID.randomUUID();
    user = new User("apply@example.com", "hash", "Apply", Role.USER);
    ReflectionTestUtils.setField(user, "id", userId);
    workout =
        new Workout(
            user,
            Instant.parse("2026-07-18T12:00:00Z"),
            WorkoutStatus.ACTIVE,
            WorkoutSource.MANUAL,
            ImportStatus.NOT_APPLICABLE);
    ReflectionTestUtils.setField(workout, "id", workoutId);
    exercise = new Exercise("Bench Press", "bench press", ExerciseCategory.CHEST, null);
    ReflectionTestUtils.setField(exercise, "id", exerciseId);
  }

  @Test
  void apply_updatesOnlyUncompletedSets() {
    WorkoutSet completed = set(1, true, "100", 8);
    WorkoutSet pending = set(2, false, "100", 8);
    CoachRecommendation rec = recommendation(exerciseId);

    when(coachRecommendationRepository.findByIdAndUserId(recommendationId, userId))
        .thenReturn(Optional.of(rec));
    when(workoutRepository.findByIdAndUserId(workoutId, userId)).thenReturn(Optional.of(workout));
    when(workoutSetRepository.findByWorkoutFetchExercise(workout))
        .thenReturn(List.of(completed, pending));
    when(workoutSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var updated =
        coachApplyService.apply(
            userId, recommendationId, new ApplyCoachRecommendationRequest(workoutId));

    assertThat(updated).hasSize(1);
    assertThat(updated.get(0).weight()).isEqualByComparingTo("105.00");
    assertThat(updated.get(0).reps()).isEqualTo(6);
    assertThat(updated.get(0).rir()).isEqualTo(2);
    assertThat(completed.getWeight()).isEqualByComparingTo("100.00");
    assertThat(pending.getWeight()).isEqualByComparingTo("105.00");

    ArgumentCaptor<WorkoutSet> savedCaptor = ArgumentCaptor.forClass(WorkoutSet.class);
    verify(workoutSetRepository).save(savedCaptor.capture());
    assertThat(savedCaptor.getAllValues()).hasSize(1);
    assertThat(savedCaptor.getValue().getSetNumber()).isEqualTo(2);
  }

  @Test
  void preview_marksCompletedSetsUnchanged() {
    WorkoutSet completed = set(1, true, "100", 8);
    WorkoutSet pending = set(2, false, "100", 8);
    CoachRecommendation rec = recommendation(exerciseId);

    when(coachRecommendationRepository.findByIdAndUserId(recommendationId, userId))
        .thenReturn(Optional.of(rec));
    when(workoutRepository.findByIdAndUserId(workoutId, userId)).thenReturn(Optional.of(workout));
    when(workoutSetRepository.findByWorkoutFetchExercise(workout))
        .thenReturn(List.of(completed, pending));

    var preview = coachApplyService.preview(userId, recommendationId, workoutId);

    assertThat(preview.sets()).hasSize(2);
    assertThat(preview.sets().get(0).willChange()).isFalse();
    assertThat(preview.sets().get(1).willChange()).isTrue();
    assertThat(preview.sets().get(1).proposedWeight()).isEqualByComparingTo("105.00");
    verify(workoutSetRepository, never()).save(any());
  }

  private CoachRecommendation recommendation(UUID scopedExerciseId) {
    String proposedChange =
        """
        {"proposedWeight":105,"proposedSets":3,"proposedRepRangeMin":6,"proposedRepRangeMax":8,"targetRir":2}
        """;
    CoachRecommendation rec =
        new CoachRecommendation(
            user,
            scopedExerciseId,
            workoutId,
            null,
            RecommendationType.WEIGHT_ADJUSTMENT,
            "{}",
            proposedChange,
            "Nudge up",
            ConfidenceLevel.MEDIUM);
    ReflectionTestUtils.setField(rec, "id", recommendationId);
    return rec;
  }

  private WorkoutSet set(int setNumber, boolean completed, String weight, int reps) {
    WorkoutSet workoutSet =
        new WorkoutSet(
            workout,
            exercise,
            new BigDecimal(weight),
            reps,
            setNumber,
            Instant.parse("2026-07-18T12:10:00Z"),
            SetType.WORKING,
            null,
            WeightEntryMode.TOTAL,
            null,
            new BigDecimal(weight),
            null,
            completed);
    ReflectionTestUtils.setField(workoutSet, "id", UUID.randomUUID());
    return workoutSet;
  }
}
