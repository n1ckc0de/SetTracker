package com.gymsync.api.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsync.api.coach.domain.CoachRecommendation;
import com.gymsync.api.coach.domain.ConfidenceLevel;
import com.gymsync.api.coach.domain.RecommendationStatus;
import com.gymsync.api.coach.domain.RecommendationType;
import com.gymsync.api.coach.dto.CoachChatRequest;
import com.gymsync.api.coach.dto.CoachRecommendationResponse;
import com.gymsync.api.coach.dto.ModifyCoachRequest;
import com.gymsync.api.coach.llm.CoachAdviceNormalizer;
import com.gymsync.api.coach.llm.CoachRecommendationGenerator;
import com.gymsync.api.coach.llm.GeneratedCoachAdvice;
import com.gymsync.api.coach.repository.CoachRecommendationRepository;
import com.gymsync.api.common.exception.ConflictException;
import com.gymsync.api.exercise.repository.ExerciseRepository;
import com.gymsync.api.readiness.domain.ReadinessCheckIn;
import com.gymsync.api.readiness.dto.CreateReadinessRequest;
import com.gymsync.api.readiness.dto.ReadinessResponse;
import com.gymsync.api.readiness.repository.ReadinessCheckInRepository;
import com.gymsync.api.readiness.service.ReadinessService;
import com.gymsync.api.stats.repository.StatsQueryRepository;
import com.gymsync.api.strength.service.Estimated1RmService;
import com.gymsync.api.user.domain.Role;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoachServiceTest {

  @Mock private CoachRecommendationRepository coachRecommendationRepository;
  @Mock private UserRepository userRepository;
  @Mock private ExerciseRepository exerciseRepository;
  @Mock private StatsQueryRepository statsQueryRepository;
  @Mock private ReadinessCheckInRepository readinessCheckInRepository;
  @Mock private ReadinessService readinessService;
  @Mock private CoachRecommendationGenerator recommendationGenerator;
  @Mock private Estimated1RmService estimated1RmService;
  @Mock private CoachApplyService coachApplyService;

  private CoachService coachService;
  private User user;
  private UUID userId;

  @BeforeEach
  void setUp() {
    coachService =
        new CoachService(
            coachRecommendationRepository,
            userRepository,
            exerciseRepository,
            statsQueryRepository,
            readinessCheckInRepository,
            readinessService,
            recommendationGenerator,
            new CoachAdviceNormalizer(new ObjectMapper()),
            estimated1RmService,
            coachApplyService,
            new ObjectMapper());
    userId = UUID.randomUUID();
    user = new User("coach-svc@example.com", "hash", "Coach", Role.USER);
  }

  @Test
  void chatWithInjuryKeywords_neverCallsGenerator() {
    UUID readinessId = UUID.randomUUID();
    when(readinessService.create(any(), any()))
        .thenReturn(
            new ReadinessResponse(
                readinessId,
                null,
                null,
                null,
                null,
                null,
                null,
                "sharp pain in shoulder",
                Instant.now()));
    ReadinessCheckIn checkIn = new ReadinessCheckIn(user);
    checkIn.setNote("sharp pain in shoulder");
    when(readinessCheckInRepository.findById(readinessId)).thenReturn(Optional.of(checkIn));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(coachRecommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CoachRecommendationResponse response =
        coachService.chat(
            userId, new CoachChatRequest("I have sharp pain in my shoulder", null, null));

    verify(recommendationGenerator, never()).generate(any());
    assertThat(response.recommendationType()).isEqualTo(RecommendationType.SAFETY_HOLD);
    assertThat(response.reasoning()).contains("medical professional");
  }

  @Test
  void chatWithoutInjury_callsGenerator() {
    UUID readinessId = UUID.randomUUID();
    when(readinessService.create(any(), any()))
        .thenAnswer(
            inv -> {
              CreateReadinessRequest req = inv.getArgument(1);
              return new ReadinessResponse(
                  readinessId,
                  req.workoutId(),
                  req.sleepHours(),
                  req.sleepQuality(),
                  req.energyLevel(),
                  req.soreness(),
                  req.stress(),
                  req.note(),
                  Instant.now());
            });
    ReadinessCheckIn checkIn = new ReadinessCheckIn(user);
    checkIn.setNote("feeling a bit tired");
    checkIn.setEnergyLevel(2);
    when(readinessCheckInRepository.findById(readinessId)).thenReturn(Optional.of(checkIn));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(recommendationGenerator.generate(any()))
        .thenReturn(
            new GeneratedCoachAdvice(
                RecommendationType.GENERAL,
                "{}",
                "{\"action\":\"MAINTAIN\"}",
                "Ease in today",
                ConfidenceLevel.LOW));
    when(coachRecommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CoachRecommendationResponse response =
        coachService.chat(userId, new CoachChatRequest("feeling a bit tired", null, null));

    verify(recommendationGenerator).generate(any());
    assertThat(response.recommendationType()).isEqualTo(RecommendationType.GENERAL);
  }

  @Test
  void acceptDismissModify_stateTransitions() {
    UUID recId = UUID.randomUUID();
    when(userRepository.getReferenceById(userId)).thenReturn(user);
    when(readinessCheckInRepository.findFirstByUserOrderByCreatedAtDesc(user))
        .thenReturn(Optional.empty());

    CoachRecommendation proposed =
        new CoachRecommendation(
            user,
            null,
            null,
            null,
            RecommendationType.GENERAL,
            "{}",
            "{\"action\":\"MAINTAIN\"}",
            "Keep going",
            ConfidenceLevel.LOW);
    when(coachRecommendationRepository.findByIdAndUserId(recId, userId))
        .thenReturn(Optional.of(proposed));
    assertThat(coachService.accept(userId, recId).status())
        .isEqualTo(RecommendationStatus.ACCEPTED);

    CoachRecommendation proposed2 =
        new CoachRecommendation(
            user,
            null,
            null,
            null,
            RecommendationType.GENERAL,
            "{}",
            "{\"action\":\"MAINTAIN\"}",
            "Keep going",
            ConfidenceLevel.LOW);
    when(coachRecommendationRepository.findByIdAndUserId(recId, userId))
        .thenReturn(Optional.of(proposed2));
    assertThat(coachService.dismiss(userId, recId).status())
        .isEqualTo(RecommendationStatus.DISMISSED);

    CoachRecommendation proposed3 =
        new CoachRecommendation(
            user,
            null,
            null,
            null,
            RecommendationType.WEIGHT_ADJUSTMENT,
            "{}",
            "{\"weightDelta\":5}",
            "Up a bit",
            ConfidenceLevel.MEDIUM);
    when(coachRecommendationRepository.findByIdAndUserId(recId, userId))
        .thenReturn(Optional.of(proposed3));
    assertThat(
            coachService
                .modify(userId, recId, new ModifyCoachRequest("{\"weightDelta\":2}", "Tweaked"))
                .status())
        .isEqualTo(RecommendationStatus.MODIFIED);

    assertThatThrownBy(() -> coachService.accept(userId, recId))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void listAndGenerate_coverHappyPaths() {
    when(userRepository.getReferenceById(userId)).thenReturn(user);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(readinessCheckInRepository.findFirstByUserOrderByCreatedAtDesc(user))
        .thenReturn(Optional.empty());
    when(recommendationGenerator.generate(any()))
        .thenReturn(
            new GeneratedCoachAdvice(
                RecommendationType.GENERAL,
                "{}",
                "{\"action\":\"MAINTAIN\"}",
                "Steady state",
                ConfidenceLevel.LOW));
    when(coachRecommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(coachRecommendationRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());
    when(coachRecommendationRepository.findByUserAndStatusOrderByCreatedAtDesc(
            user, RecommendationStatus.PROPOSED))
        .thenReturn(List.of());

    assertThat(coachService.list(userId, null)).isEmpty();
    assertThat(coachService.list(userId, RecommendationStatus.PROPOSED)).isEmpty();
    assertThat(
            coachService
                .generate(userId, new com.gymsync.api.coach.dto.GenerateCoachRequest(null, null))
                .recommendationType())
        .isEqualTo(RecommendationType.GENERAL);
    verify(recommendationGenerator).generate(any());
  }

  @Test
  void generate_withInjuryNote_skipsGenerator() {
    when(userRepository.getReferenceById(userId)).thenReturn(user);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    ReadinessCheckIn checkIn = new ReadinessCheckIn(user);
    checkIn.setNote("possible injury on knee");
    when(readinessCheckInRepository.findFirstByUserOrderByCreatedAtDesc(user))
        .thenReturn(Optional.of(checkIn));
    when(coachRecommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CoachRecommendationResponse response =
        coachService.generate(
            userId, new com.gymsync.api.coach.dto.GenerateCoachRequest(null, null));

    verify(recommendationGenerator, never()).generate(any());
    assertThat(response.recommendationType()).isEqualTo(RecommendationType.SAFETY_HOLD);
  }
}
