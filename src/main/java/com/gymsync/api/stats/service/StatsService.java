package com.gymsync.api.stats.service;

import com.gymsync.api.common.exception.NotFoundException;
import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.exercise.domain.ExerciseCategory;
import com.gymsync.api.exercise.repository.ExerciseRepository;
import com.gymsync.api.stats.dto.CategoryVolume;
import com.gymsync.api.stats.dto.ExerciseVolume;
import com.gymsync.api.stats.dto.NextSessionRecommendationResponse;
import com.gymsync.api.stats.dto.PersonalRecordResponse;
import com.gymsync.api.stats.dto.PrHistoryResponse;
import com.gymsync.api.stats.dto.PreviousPerformance;
import com.gymsync.api.stats.dto.RecommendationOptions;
import com.gymsync.api.stats.dto.VolumeTrendResponse;
import com.gymsync.api.stats.dto.WeeklyComparison;
import com.gymsync.api.stats.dto.WeeklyStatsResponse;
import com.gymsync.api.stats.repository.StatsQueryRepository;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import com.gymsync.api.workout.domain.WorkoutSet;
import com.gymsync.api.workout.repository.WorkoutRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weekly training statistics, personal records, and progressive-overload recommendations. Every
 * calculation is scoped to "countable" data: sets belonging to a {@code COMPLETED} workout that is
 * either not an import ({@code NOT_APPLICABLE}) or a {@code CONFIRMED} import (spec sections
 * 14/15/22) — pending-review and rejected imports never contribute.
 */
@Service
public class StatsService {

  private static final String NO_HISTORY_MESSAGE =
      "No confirmed, completed workouts were found for this exercise yet.";
  private static final String RECOMMENDATION_DISCLAIMER =
      "This is an automated suggestion generated from your own logged workout history using "
          + "simple, deterministic rules. It is informational only and is not medical or "
          + "professional training advice.";
  private static final String PR_DISCLAIMER =
      "Personal records (including the estimated one-rep max) are calculated only from your "
          + "own logged, confirmed workout history and are informational only, not a measured "
          + "or medically validated maximum.";

  private final StatsQueryRepository statsQueryRepository;
  private final WorkoutRepository workoutRepository;
  private final UserRepository userRepository;
  private final ExerciseRepository exerciseRepository;
  private final OverloadRecommendationEngine recommendationEngine;

  public StatsService(
      StatsQueryRepository statsQueryRepository,
      WorkoutRepository workoutRepository,
      UserRepository userRepository,
      ExerciseRepository exerciseRepository,
      OverloadRecommendationEngine recommendationEngine) {
    this.statsQueryRepository = statsQueryRepository;
    this.workoutRepository = workoutRepository;
    this.userRepository = userRepository;
    this.exerciseRepository = exerciseRepository;
    this.recommendationEngine = recommendationEngine;
  }

  @Transactional(readOnly = true)
  public WeeklyStatsResponse weeklyStats(UUID userId, LocalDate referenceDate) {
    User user = userRepository.getReferenceById(userId);
    LocalDate anchor = referenceDate != null ? referenceDate : LocalDate.now(ZoneOffset.UTC);
    LocalDate weekStart = anchor.with(DayOfWeek.MONDAY);
    LocalDate weekEnd = weekStart.plusDays(6);

    Instant from = startOfDayUtc(weekStart);
    Instant to = startOfDayUtc(weekStart.plusDays(7));
    WeekAggregate current =
        aggregate(statsQueryRepository.findCountableSetsBetween(userId, from, to));
    long completedWorkouts = workoutRepository.countCountableWorkouts(user, from, to);

    LocalDate priorWeekStart = weekStart.minusWeeks(1);
    Instant priorFrom = startOfDayUtc(priorWeekStart);
    Instant priorTo = from;
    WeekAggregate prior =
        aggregate(statsQueryRepository.findCountableSetsBetween(userId, priorFrom, priorTo));

    WeeklyComparison comparison = compare(current.totalVolume, prior.totalVolume);

    return new WeeklyStatsResponse(
        weekStart,
        weekEnd,
        completedWorkouts,
        current.totalSets,
        current.totalReps,
        current.totalVolume,
        current.volumeByExercise(),
        current.volumeByCategory(),
        comparison);
  }

  @Transactional(readOnly = true)
  public VolumeTrendResponse volumeTrend(UUID userId, int weeks) {
    int window = Math.min(52, Math.max(1, weeks));
    LocalDate anchor = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);
    List<VolumeTrendResponse.WeeklyVolumePoint> points = new ArrayList<>();
    for (int i = window - 1; i >= 0; i--) {
      LocalDate weekStart = anchor.minusWeeks(i);
      LocalDate weekEnd = weekStart.plusDays(6);
      Instant from = startOfDayUtc(weekStart);
      Instant to = startOfDayUtc(weekStart.plusDays(7));
      WeekAggregate agg =
          aggregate(statsQueryRepository.findCountableSetsBetween(userId, from, to));
      points.add(new VolumeTrendResponse.WeeklyVolumePoint(weekStart, weekEnd, agg.totalVolume()));
    }
    return new VolumeTrendResponse(window, points);
  }

  @Transactional(readOnly = true)
  public PrHistoryResponse prHistory(UUID userId, UUID exerciseId) {
    requireVisibleExercise(userId, exerciseId);
    List<WorkoutSet> sets = statsQueryRepository.findCountableSetsForExercise(userId, exerciseId);
    if (sets.isEmpty()) {
      return new PrHistoryResponse(exerciseId, List.of(), PR_DISCLAIMER);
    }
    // One point per session: that session's best estimated 1RM (chronological trend).
    Map<UUID, WorkoutSet> bestPerWorkout = new LinkedHashMap<>();
    List<WorkoutSet> chronological =
        sets.stream()
            .sorted(Comparator.comparing((WorkoutSet s) -> s.getWorkout().getStartedAt()))
            .toList();
    for (WorkoutSet set : chronological) {
      UUID wid = set.getWorkout().getId();
      bestPerWorkout.merge(
          wid,
          set,
          (a, b) ->
              estimateOneRepMax(a.getWeight(), a.getReps())
                          .compareTo(estimateOneRepMax(b.getWeight(), b.getReps()))
                      >= 0
                  ? a
                  : b);
    }
    List<PrHistoryResponse.PrHistoryPoint> points = new ArrayList<>();
    for (WorkoutSet set :
        bestPerWorkout.values().stream()
            .sorted(Comparator.comparing(s -> s.getWorkout().getStartedAt()))
            .toList()) {
      points.add(
          new PrHistoryResponse.PrHistoryPoint(
              set.getWorkout().getStartedAt(),
              set.getWorkout().getId(),
              set.getWeight(),
              set.getReps(),
              estimateOneRepMax(set.getWeight(), set.getReps())));
    }
    return new PrHistoryResponse(exerciseId, points, PR_DISCLAIMER);
  }

  @Transactional(readOnly = true)
  public PersonalRecordResponse personalRecord(UUID userId, UUID exerciseId) {
    requireVisibleExercise(userId, exerciseId);
    List<WorkoutSet> sets = statsQueryRepository.findCountableSetsForExercise(userId, exerciseId);
    if (sets.isEmpty()) {
      throw new NotFoundException(NO_HISTORY_MESSAGE);
    }

    WorkoutSet heaviest =
        sets.stream()
            .max(Comparator.comparing(WorkoutSet::getWeight).thenComparing(WorkoutSet::getReps))
            .orElseThrow();
    int highestRepsAtHeaviestWeight =
        sets.stream()
            .filter(s -> s.getWeight().compareTo(heaviest.getWeight()) == 0)
            .mapToInt(WorkoutSet::getReps)
            .max()
            .orElse(heaviest.getReps());

    WorkoutSet bestOneRepMaxSet =
        sets.stream()
            .max(Comparator.comparing(s -> estimateOneRepMax(s.getWeight(), s.getReps())))
            .orElseThrow();
    BigDecimal bestOneRepMax =
        estimateOneRepMax(bestOneRepMaxSet.getWeight(), bestOneRepMaxSet.getReps());

    return new PersonalRecordResponse(
        exerciseId,
        heaviest.getWeight(),
        heaviest.getReps(),
        highestRepsAtHeaviestWeight,
        heaviest.getWorkout().getStartedAt(),
        heaviest.getWorkout().getId(),
        bestOneRepMax,
        bestOneRepMaxSet.getWorkout().getStartedAt(),
        bestOneRepMaxSet.getWorkout().getId(),
        PR_DISCLAIMER);
  }

  @Transactional(readOnly = true)
  public NextSessionRecommendationResponse nextSessionRecommendation(UUID userId, UUID exerciseId) {
    requireVisibleExercise(userId, exerciseId);
    List<WorkoutSet> sets = statsQueryRepository.findCountableSetsForExercise(userId, exerciseId);
    if (sets.isEmpty()) {
      throw new NotFoundException(NO_HISTORY_MESSAGE);
    }

    UUID mostRecentWorkoutId = sets.get(0).getWorkout().getId();
    WorkoutSet topSetOfMostRecentWorkout =
        sets.stream()
            .filter(s -> s.getWorkout().getId().equals(mostRecentWorkoutId))
            .max(Comparator.comparing(WorkoutSet::getWeight).thenComparing(WorkoutSet::getReps))
            .orElseThrow();

    RecommendationResult result =
        recommendationEngine.recommend(
            topSetOfMostRecentWorkout.getWeight(), topSetOfMostRecentWorkout.getReps());

    return new NextSessionRecommendationResponse(
        exerciseId,
        mostRecentWorkoutId,
        new PreviousPerformance(
            topSetOfMostRecentWorkout.getWeight(), topSetOfMostRecentWorkout.getReps()),
        new RecommendationOptions(result.optionA(), result.optionB()),
        result.reason(),
        RECOMMENDATION_DISCLAIMER);
  }

  private void requireVisibleExercise(UUID userId, UUID exerciseId) {
    Exercise exercise =
        exerciseRepository
            .findById(exerciseId)
            .orElseThrow(() -> new NotFoundException("Exercise not found."));
    boolean visible = exercise.isGlobal() || exercise.getCreatedBy().getId().equals(userId);
    if (!visible) {
      throw new NotFoundException("Exercise not found.");
    }
  }

  private static BigDecimal estimateOneRepMax(BigDecimal weight, int reps) {
    // Epley formula: 1RM = weight * (1 + reps / 30)
    BigDecimal factor =
        BigDecimal.ONE.add(
            BigDecimal.valueOf(reps).divide(BigDecimal.valueOf(30), 10, RoundingMode.HALF_UP));
    return weight.multiply(factor).setScale(2, RoundingMode.HALF_UP);
  }

  private static Instant startOfDayUtc(LocalDate date) {
    return date.atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  private static WeeklyComparison compare(BigDecimal currentVolume, BigDecimal priorVolume) {
    BigDecimal change = currentVolume.subtract(priorVolume);
    Double changePercent =
        priorVolume.compareTo(BigDecimal.ZERO) == 0
            ? null
            : change
                .divide(priorVolume, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    return new WeeklyComparison(priorVolume, change, changePercent);
  }

  private static WeekAggregate aggregate(List<WorkoutSet> sets) {
    long totalSets = sets.size();
    long totalReps = 0;
    BigDecimal totalVolume = BigDecimal.ZERO;
    Map<UUID, ExerciseAccumulator> byExercise = new LinkedHashMap<>();
    Map<ExerciseCategory, BigDecimal> byCategory = new LinkedHashMap<>();

    for (WorkoutSet set : sets) {
      BigDecimal volume = set.getWeight().multiply(BigDecimal.valueOf(set.getReps()));
      totalReps += set.getReps();
      totalVolume = totalVolume.add(volume);

      Exercise exercise = set.getExercise();
      byExercise
          .computeIfAbsent(
              exercise.getId(), id -> new ExerciseAccumulator(exercise.getName(), BigDecimal.ZERO))
          .add(volume);
      byCategory.merge(exercise.getCategory(), volume, BigDecimal::add);
    }

    List<ExerciseVolume> volumeByExercise =
        byExercise.entrySet().stream()
            .map(e -> new ExerciseVolume(e.getKey(), e.getValue().name, e.getValue().volume))
            .sorted(Comparator.comparing(ExerciseVolume::volume).reversed())
            .toList();
    List<CategoryVolume> volumeByCategory =
        byCategory.entrySet().stream()
            .map(e -> new CategoryVolume(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(CategoryVolume::volume).reversed())
            .toList();

    return new WeekAggregate(totalSets, totalReps, totalVolume, volumeByExercise, volumeByCategory);
  }

  private static final class ExerciseAccumulator {
    private final String name;
    private BigDecimal volume;

    private ExerciseAccumulator(String name, BigDecimal volume) {
      this.name = name;
      this.volume = volume;
    }

    private void add(BigDecimal delta) {
      this.volume = this.volume.add(delta);
    }
  }

  private record WeekAggregate(
      long totalSets,
      long totalReps,
      BigDecimal totalVolume,
      List<ExerciseVolume> volumeByExercise,
      List<CategoryVolume> volumeByCategory) {}
}
