package com.gymsync.api.coach.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gymsync.api.coach.bounds.CoachBoundsCalculator;
import com.gymsync.api.coach.domain.CoachRecommendation;
import com.gymsync.api.coach.domain.ConfidenceLevel;
import com.gymsync.api.coach.domain.RecommendationStatus;
import com.gymsync.api.coach.domain.RecommendationType;
import com.gymsync.api.coach.dto.ApplyCoachRecommendationRequest;
import com.gymsync.api.coach.dto.CoachApplyPreviewResponse;
import com.gymsync.api.coach.dto.CoachChatRequest;
import com.gymsync.api.coach.dto.CoachRecommendationResponse;
import com.gymsync.api.coach.dto.GenerateCoachRequest;
import com.gymsync.api.coach.dto.ModifyCoachRequest;
import com.gymsync.api.coach.llm.CoachAdviceNormalizer;
import com.gymsync.api.coach.llm.CoachContext;
import com.gymsync.api.coach.llm.CoachRecommendationGenerator;
import com.gymsync.api.coach.llm.GeneratedCoachAdvice;
import com.gymsync.api.coach.llm.OneRmQuestionParser;
import com.gymsync.api.coach.llm.OneRmQuestionParser.ParsedOneRmQuestion;
import com.gymsync.api.coach.repository.CoachRecommendationRepository;
import com.gymsync.api.common.exception.ConflictException;
import com.gymsync.api.common.exception.NotFoundException;
import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.exercise.repository.ExerciseRepository;
import com.gymsync.api.readiness.domain.ReadinessCheckIn;
import com.gymsync.api.readiness.dto.CreateReadinessRequest;
import com.gymsync.api.readiness.repository.ReadinessCheckInRepository;
import com.gymsync.api.readiness.service.ReadinessService;
import com.gymsync.api.stats.repository.StatsQueryRepository;
import com.gymsync.api.strength.service.Estimated1RmService;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import com.gymsync.api.workout.domain.WorkoutSet;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachService {

  private static final int MAX_RECENT_SESSIONS = 5;

  private final CoachRecommendationRepository coachRecommendationRepository;
  private final UserRepository userRepository;
  private final ExerciseRepository exerciseRepository;
  private final StatsQueryRepository statsQueryRepository;
  private final ReadinessCheckInRepository readinessCheckInRepository;
  private final ReadinessService readinessService;
  private final CoachRecommendationGenerator recommendationGenerator;
  private final CoachAdviceNormalizer adviceNormalizer;
  private final Estimated1RmService estimated1RmService;
  private final CoachApplyService coachApplyService;
  private final ObjectMapper objectMapper;

  public CoachService(
      CoachRecommendationRepository coachRecommendationRepository,
      UserRepository userRepository,
      ExerciseRepository exerciseRepository,
      StatsQueryRepository statsQueryRepository,
      ReadinessCheckInRepository readinessCheckInRepository,
      ReadinessService readinessService,
      CoachRecommendationGenerator recommendationGenerator,
      CoachAdviceNormalizer adviceNormalizer,
      Estimated1RmService estimated1RmService,
      CoachApplyService coachApplyService,
      ObjectMapper objectMapper) {
    this.coachRecommendationRepository = coachRecommendationRepository;
    this.userRepository = userRepository;
    this.exerciseRepository = exerciseRepository;
    this.statsQueryRepository = statsQueryRepository;
    this.readinessCheckInRepository = readinessCheckInRepository;
    this.readinessService = readinessService;
    this.recommendationGenerator = recommendationGenerator;
    this.adviceNormalizer = adviceNormalizer;
    this.estimated1RmService = estimated1RmService;
    this.coachApplyService = coachApplyService;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<CoachRecommendationResponse> list(UUID userId, RecommendationStatus status) {
    User user = userRepository.getReferenceById(userId);
    List<CoachRecommendation> rows =
        status == null
            ? coachRecommendationRepository.findByUserOrderByCreatedAtDesc(user)
            : coachRecommendationRepository.findByUserAndStatusOrderByCreatedAtDesc(user, status);
    return rows.stream().map(CoachRecommendationResponse::from).toList();
  }

  @Transactional
  public CoachRecommendationResponse generate(UUID userId, GenerateCoachRequest request) {
    UUID exerciseId = request == null ? null : request.exerciseId();
    UUID workoutId = request == null ? null : request.workoutId();
    ReadinessCheckIn readiness = latestReadiness(userId);
    boolean injury =
        readiness != null
            && CoachBoundsCalculator.containsInjuryOrMedicalConcern(readiness.getNote());
    return persistFromContext(
        userId, buildContext(userId, exerciseId, workoutId, readiness, injury));
  }

  @Transactional
  public CoachRecommendationResponse chat(UUID userId, CoachChatRequest request) {
    if (!CoachBoundsCalculator.containsInjuryOrMedicalConcern(request.message())) {
      var oneRm = OneRmQuestionParser.parse(request.message());
      if (oneRm.isPresent()) {
        return persistOneRmAnswer(userId, request, oneRm.get());
      }
    }

    CreateReadinessRequest extracted =
        ChatReadinessExtractor.extract(request.message(), request.workoutId());
    var saved = readinessService.create(userId, extracted);
    ReadinessCheckIn readiness =
        readinessCheckInRepository
            .findById(saved.id())
            .orElseThrow(() -> new NotFoundException("Readiness check-in not found."));

    // Injury/pain keywords: fixed safety response — never call the LLM generator.
    boolean injury =
        CoachBoundsCalculator.containsInjuryOrMedicalConcern(request.message())
            || CoachBoundsCalculator.containsInjuryOrMedicalConcern(readiness.getNote());
    CoachContext context =
        buildContext(userId, request.exerciseId(), request.workoutId(), readiness, injury);
    if (injury) {
      GeneratedCoachAdvice advice =
          adviceNormalizer.safetyHoldAdvice(adviceNormalizer.buildPreviousPerformance(context));
      return persistAdvice(userId, context, advice);
    }
    return persistFromContext(userId, context);
  }

  @Transactional
  public CoachRecommendationResponse accept(UUID userId, UUID id) {
    CoachRecommendation rec = requireOwned(userId, id);
    try {
      rec.accept();
    } catch (IllegalStateException ex) {
      throw new ConflictException(ex.getMessage());
    }
    return CoachRecommendationResponse.from(rec);
  }

  @Transactional
  public CoachRecommendationResponse dismiss(UUID userId, UUID id) {
    CoachRecommendation rec = requireOwned(userId, id);
    try {
      rec.dismiss();
    } catch (IllegalStateException ex) {
      throw new ConflictException(ex.getMessage());
    }
    return CoachRecommendationResponse.from(rec);
  }

  @Transactional
  public CoachRecommendationResponse modify(UUID userId, UUID id, ModifyCoachRequest request) {
    CoachRecommendation rec = requireOwned(userId, id);
    // Clamp modified payload against current exercise bounds when possible.
    ReadinessCheckIn readiness = latestReadiness(userId);
    boolean injury =
        readiness != null
            && CoachBoundsCalculator.containsInjuryOrMedicalConcern(readiness.getNote());
    CoachContext context =
        buildContext(userId, rec.getExerciseId(), rec.getBasedOnWorkoutId(), readiness, injury);
    GeneratedCoachAdvice normalized =
        adviceNormalizer.normalize(
            new GeneratedCoachAdvice(
                rec.getRecommendationType(),
                rec.getPreviousPerformance(),
                request.proposedChange(),
                request.reasoning() != null ? request.reasoning() : rec.getReasoning(),
                rec.getConfidenceLevel()),
            context);
    try {
      rec.modify(normalized.proposedChangeJson(), normalized.reasoning());
    } catch (IllegalStateException ex) {
      throw new ConflictException(ex.getMessage());
    }
    return CoachRecommendationResponse.from(rec);
  }

  @Transactional(readOnly = true)
  public CoachApplyPreviewResponse applyPreview(UUID userId, UUID id, UUID workoutId) {
    return coachApplyService.preview(userId, id, workoutId);
  }

  @Transactional
  public List<com.gymsync.api.workout.dto.WorkoutSetResponse> applyToSession(
      UUID userId, UUID id, ApplyCoachRecommendationRequest request) {
    return coachApplyService.apply(userId, id, request);
  }

  private CoachRecommendationResponse persistOneRmAnswer(
      UUID userId, CoachChatRequest request, ParsedOneRmQuestion parsed) {
    var estimate =
        estimated1RmService.estimate(
            new Estimated1RmService.EstimateRequest(parsed.weight(), parsed.reps(), null));
    ObjectNode change = objectMapper.createObjectNode();
    change.put("action", "ESTIMATE_1RM");
    change.put("inputWeight", parsed.weight());
    change.put("inputReps", parsed.reps());
    change.put("workingEstimate", estimate.workingEstimate());
    change.put("rangeMin", estimate.rangeMin());
    change.put("rangeMax", estimate.rangeMax());
    change.put("epley", estimate.epley());
    change.put("brzycki", estimate.brzycki());
    change.put("lowConfidence", estimate.lowConfidence());

    String reasoning =
        String.format(
            "Based on %s×%d, your working estimated 1RM is %s (range %s–%s). "
                + "Epley: %s, Brzycki: %s.%s",
            parsed.weight(),
            parsed.reps(),
            estimate.workingEstimate(),
            estimate.rangeMin(),
            estimate.rangeMax(),
            estimate.epley(),
            estimate.brzycki(),
            estimate.lowConfidence() ? " High rep count — treat as low confidence." : "");

    CoachContext context =
        buildContext(
            userId, request.exerciseId(), request.workoutId(), latestReadiness(userId), false);
    GeneratedCoachAdvice advice =
        new GeneratedCoachAdvice(
            RecommendationType.GENERAL,
            adviceNormalizer.buildPreviousPerformance(context),
            change.toString(),
            reasoning,
            estimate.lowConfidence() ? ConfidenceLevel.LOW : ConfidenceLevel.HIGH);
    return persistAdvice(userId, context, advice);
  }

  private CoachRecommendationResponse persistFromContext(UUID userId, CoachContext context) {
    if (context.safetyHold()) {
      GeneratedCoachAdvice advice =
          adviceNormalizer.safetyHoldAdvice(adviceNormalizer.buildPreviousPerformance(context));
      return persistAdvice(userId, context, advice);
    }
    GeneratedCoachAdvice advice = recommendationGenerator.generate(context);
    return persistAdvice(userId, context, advice);
  }

  private CoachRecommendationResponse persistAdvice(
      UUID userId, CoachContext context, GeneratedCoachAdvice advice) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found."));
    GeneratedCoachAdvice normalized = adviceNormalizer.normalize(advice, context);
    CoachRecommendation entity =
        new CoachRecommendation(
            user,
            context.exerciseId(),
            context.basedOnWorkoutId(),
            context.readinessCheckInId(),
            normalized.type(),
            normalized.previousPerformanceJson(),
            normalized.proposedChangeJson(),
            normalized.reasoning(),
            normalized.confidence());
    return CoachRecommendationResponse.from(coachRecommendationRepository.save(entity));
  }

  private CoachRecommendation requireOwned(UUID userId, UUID id) {
    return coachRecommendationRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Coach recommendation not found."));
  }

  private ReadinessCheckIn latestReadiness(UUID userId) {
    User user = userRepository.getReferenceById(userId);
    return readinessCheckInRepository.findFirstByUserOrderByCreatedAtDesc(user).orElse(null);
  }

  private CoachContext buildContext(
      UUID userId,
      UUID exerciseId,
      UUID workoutId,
      ReadinessCheckIn readiness,
      boolean safetyHold) {
    String exerciseName = null;
    List<BigDecimal> sessionTops = List.of();
    Integer recentSetCount = null;
    CoachBoundsCalculator.WeightBounds weightBounds = null;
    int maxSetsRemovable = 0;
    UUID basedOnWorkoutId = workoutId;

    if (exerciseId != null) {
      Exercise exercise = requireVisibleExercise(userId, exerciseId);
      exerciseName = exercise.getName();
      List<WorkoutSet> sets = statsQueryRepository.findCountableSetsForExercise(userId, exerciseId);
      SessionHistory history = summarizeSessions(sets);
      sessionTops = history.sessionTopWeights();
      recentSetCount = history.mostRecentSetCount();
      basedOnWorkoutId =
          basedOnWorkoutId != null ? basedOnWorkoutId : history.mostRecentWorkoutId();
      if (history.lastWeight() != null) {
        weightBounds = CoachBoundsCalculator.weightAdjustmentBounds(history.lastWeight());
      }
      if (recentSetCount != null) {
        maxSetsRemovable = CoachBoundsCalculator.maxSetsRemovable(recentSetCount);
      }
    }

    Integer soreness = readiness == null ? null : readiness.getSoreness();
    Integer stress = readiness == null ? null : readiness.getStress();
    boolean deload =
        !safetyHold && CoachBoundsCalculator.shouldRecommendDeload(sessionTops, soreness, stress);

    return new CoachContext(
        userId,
        exerciseId,
        basedOnWorkoutId,
        readiness == null ? null : readiness.getId(),
        exerciseName,
        sessionTops,
        recentSetCount,
        CoachContext.ReadinessSnapshot.from(readiness),
        weightBounds,
        maxSetsRemovable,
        deload,
        safetyHold,
        safetyHold ? CoachAdviceNormalizer.SAFETY_REASONING : null);
  }

  private Exercise requireVisibleExercise(UUID userId, UUID exerciseId) {
    Exercise exercise =
        exerciseRepository
            .findById(exerciseId)
            .orElseThrow(() -> new NotFoundException("Exercise not found."));
    boolean visible = exercise.isGlobal() || exercise.getCreatedBy().getId().equals(userId);
    if (!visible) {
      throw new NotFoundException("Exercise not found.");
    }
    return exercise;
  }

  private static SessionHistory summarizeSessions(List<WorkoutSet> sets) {
    if (sets == null || sets.isEmpty()) {
      return new SessionHistory(List.of(), null, null, null);
    }
    Map<UUID, SessionAgg> byWorkout = new LinkedHashMap<>();
    for (WorkoutSet set : sets) {
      UUID wid = set.getWorkout().getId();
      byWorkout
          .computeIfAbsent(wid, id -> new SessionAgg(id, set.getWorkout().getStartedAt()))
          .add(set);
    }
    List<SessionAgg> ordered =
        byWorkout.values().stream().sorted(Comparator.comparing(SessionAgg::startedAt)).toList();
    List<SessionAgg> window =
        ordered.size() <= MAX_RECENT_SESSIONS
            ? ordered
            : ordered.subList(ordered.size() - MAX_RECENT_SESSIONS, ordered.size());
    List<BigDecimal> tops = new ArrayList<>();
    for (SessionAgg agg : window) {
      tops.add(agg.topWeight);
    }
    SessionAgg mostRecent = ordered.get(ordered.size() - 1);
    return new SessionHistory(
        tops, mostRecent.topWeight, mostRecent.setCount, mostRecent.workoutId);
  }

  private static final class SessionAgg {
    private final UUID workoutId;
    private final java.time.Instant startedAt;
    private BigDecimal topWeight = BigDecimal.ZERO;
    private int setCount;

    private SessionAgg(UUID workoutId, java.time.Instant startedAt) {
      this.workoutId = workoutId;
      this.startedAt = startedAt;
    }

    private void add(WorkoutSet set) {
      setCount++;
      if (set.getWeight().compareTo(topWeight) > 0) {
        topWeight = set.getWeight();
      }
    }

    private java.time.Instant startedAt() {
      return startedAt;
    }
  }

  private record SessionHistory(
      List<BigDecimal> sessionTopWeights,
      BigDecimal lastWeight,
      Integer mostRecentSetCount,
      UUID mostRecentWorkoutId) {}
}
