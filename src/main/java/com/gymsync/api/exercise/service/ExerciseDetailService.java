package com.gymsync.api.exercise.service;

import com.gymsync.api.coach.domain.RecommendationStatus;
import com.gymsync.api.coach.dto.CoachRecommendationResponse;
import com.gymsync.api.coach.repository.CoachRecommendationRepository;
import com.gymsync.api.exercise.dto.ExerciseDetailResponse;
import com.gymsync.api.exercise.dto.ExerciseResponse;
import com.gymsync.api.stats.dto.PersonalRecordResponse;
import com.gymsync.api.stats.dto.PrHistoryResponse;
import com.gymsync.api.stats.repository.StatsQueryRepository;
import com.gymsync.api.stats.service.StatsService;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import com.gymsync.api.workout.domain.WorkoutSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExerciseDetailService {

  private static final int RECENT_SET_LIMIT = 20;

  private final ExerciseService exerciseService;
  private final StatsService statsService;
  private final StatsQueryRepository statsQueryRepository;
  private final CoachRecommendationRepository coachRecommendationRepository;
  private final UserRepository userRepository;

  public ExerciseDetailService(
      ExerciseService exerciseService,
      StatsService statsService,
      StatsQueryRepository statsQueryRepository,
      CoachRecommendationRepository coachRecommendationRepository,
      UserRepository userRepository) {
    this.exerciseService = exerciseService;
    this.statsService = statsService;
    this.statsQueryRepository = statsQueryRepository;
    this.coachRecommendationRepository = coachRecommendationRepository;
    this.userRepository = userRepository;
  }

  @Transactional(readOnly = true)
  public ExerciseDetailResponse detail(UUID userId, UUID exerciseId) {
    ExerciseResponse exercise = exerciseService.getExercise(userId, exerciseId);
    PrHistoryResponse prHistory = statsService.prHistory(userId, exerciseId);

    // Load sets first. Do not call personalRecord() when empty — that method throws
    // NotFoundException on a nested @Transactional proxy, which marks this transaction
    // rollback-only even if caught here (UnexpectedRollbackException → opaque 500).
    List<WorkoutSet> sets = statsQueryRepository.findCountableSetsForExercise(userId, exerciseId);
    PersonalRecordResponse personalRecord =
        sets.isEmpty() ? null : statsService.personalRecord(userId, exerciseId);

    List<ExerciseDetailResponse.RecentSetRow> recentSets =
        sets.stream()
            .limit(RECENT_SET_LIMIT)
            .map(
                s ->
                    new ExerciseDetailResponse.RecentSetRow(
                        s.getId(),
                        s.getWorkout().getId(),
                        s.getLoggedAt(),
                        s.getWeight(),
                        s.getReps(),
                        s.getSetNumber()))
            .toList();

    User user = userRepository.getReferenceById(userId);
    CoachRecommendationResponse activeRecommendation =
        coachRecommendationRepository
            .findFirstByUserAndExerciseIdAndStatusOrderByCreatedAtDesc(
                user, exerciseId, RecommendationStatus.PROPOSED)
            .map(CoachRecommendationResponse::from)
            .orElse(null);

    return new ExerciseDetailResponse(
        exercise, personalRecord, prHistory, recentSets, activeRecommendation);
  }
}
