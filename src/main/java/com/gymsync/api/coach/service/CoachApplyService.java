package com.gymsync.api.coach.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsync.api.coach.domain.CoachRecommendation;
import com.gymsync.api.coach.domain.RecommendationStatus;
import com.gymsync.api.coach.dto.ApplyCoachRecommendationRequest;
import com.gymsync.api.coach.dto.CoachApplyPreviewResponse;
import com.gymsync.api.coach.repository.CoachRecommendationRepository;
import com.gymsync.api.common.exception.BadRequestException;
import com.gymsync.api.common.exception.ConflictException;
import com.gymsync.api.common.exception.NotFoundException;
import com.gymsync.api.workout.domain.SetType;
import com.gymsync.api.workout.domain.WeightEntryMode;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.domain.WorkoutSet;
import com.gymsync.api.workout.domain.WorkoutStatus;
import com.gymsync.api.workout.dto.WorkoutSetResponse;
import com.gymsync.api.workout.repository.WorkoutRepository;
import com.gymsync.api.workout.repository.WorkoutSetRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachApplyService {

  private final CoachRecommendationRepository coachRecommendationRepository;
  private final WorkoutRepository workoutRepository;
  private final WorkoutSetRepository workoutSetRepository;
  private final ObjectMapper objectMapper;

  public CoachApplyService(
      CoachRecommendationRepository coachRecommendationRepository,
      WorkoutRepository workoutRepository,
      WorkoutSetRepository workoutSetRepository,
      ObjectMapper objectMapper) {
    this.coachRecommendationRepository = coachRecommendationRepository;
    this.workoutRepository = workoutRepository;
    this.workoutSetRepository = workoutSetRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public CoachApplyPreviewResponse preview(UUID userId, UUID recommendationId, UUID workoutId) {
    CoachRecommendation rec = requireOwned(userId, recommendationId);
    Workout workout = requireActiveWorkout(userId, workoutId);
    ParsedProposal proposal = parseProposal(rec.getProposedChange());
    List<WorkoutSet> exerciseSets = setsForExercise(workout, rec.getExerciseId());
    List<CoachApplyPreviewResponse.SetPreview> previews = new ArrayList<>();
    for (WorkoutSet set : exerciseSets) {
      if (set.isCompleted()) {
        previews.add(CoachApplyPreviewResponse.SetPreview.unchanged(set));
      } else {
        previews.add(
            CoachApplyPreviewResponse.SetPreview.proposed(
                set, proposal.proposedWeight(), targetReps(proposal), proposal.targetRir()));
      }
    }
    return new CoachApplyPreviewResponse(workoutId, rec.getExerciseId(), previews);
  }

  @Transactional
  public List<WorkoutSetResponse> apply(
      UUID userId, UUID recommendationId, ApplyCoachRecommendationRequest request) {
    CoachRecommendation rec = requireOwned(userId, recommendationId);
    if (rec.getStatus() != RecommendationStatus.PROPOSED
        && rec.getStatus() != RecommendationStatus.MODIFIED) {
      throw new ConflictException("Only proposed or modified recommendations can be applied.");
    }
    Workout workout = requireActiveWorkout(userId, request.workoutId());
    ParsedProposal proposal = parseProposal(rec.getProposedChange());
    List<WorkoutSet> exerciseSets = setsForExercise(workout, rec.getExerciseId());
    List<WorkoutSet> incomplete =
        exerciseSets.stream()
            .filter(s -> !s.isCompleted())
            .sorted(Comparator.comparingInt(WorkoutSet::getSetNumber))
            .toList();

    if (incomplete.isEmpty()) {
      throw new BadRequestException("No uncompleted sets to update for this exercise.");
    }

    int targetReps = targetReps(proposal);
    List<WorkoutSetResponse> updated = new ArrayList<>();
    for (WorkoutSet set : incomplete) {
      set.update(
          proposal.proposedWeight(),
          WeightEntryMode.TOTAL,
          null,
          targetReps,
          set.getSetNumber(),
          SetType.WORKING,
          proposal.targetRir(),
          set.getNote(),
          false);
      updated.add(WorkoutSetResponse.from(workoutSetRepository.save(set)));
    }
    rec.accept();
    return updated;
  }

  private int targetReps(ParsedProposal proposal) {
    return proposal.proposedRepRangeMin();
  }

  private List<WorkoutSet> setsForExercise(Workout workout, UUID exerciseId) {
    if (exerciseId == null) {
      throw new BadRequestException("Recommendation is not scoped to an exercise.");
    }
    return workoutSetRepository.findByWorkoutFetchExercise(workout).stream()
        .filter(s -> s.getExercise().getId().equals(exerciseId))
        .sorted(Comparator.comparingInt(WorkoutSet::getSetNumber))
        .toList();
  }

  private ParsedProposal parseProposal(String proposedChangeJson) {
    try {
      JsonNode node = objectMapper.readTree(proposedChangeJson);
      if (!node.has("proposedWeight")) {
        throw new BadRequestException("Recommendation has no structured load targets to apply.");
      }
      return new ParsedProposal(
          node.get("proposedWeight").decimalValue(),
          node.path("proposedSets").asInt(3),
          node.path("proposedRepRangeMin").asInt(6),
          node.path("proposedRepRangeMax").asInt(8),
          node.path("targetRir").asInt(2));
    } catch (BadRequestException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BadRequestException("Malformed proposedChange JSON.");
    }
  }

  private CoachRecommendation requireOwned(UUID userId, UUID id) {
    return coachRecommendationRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Coach recommendation not found."));
  }

  private Workout requireActiveWorkout(UUID userId, UUID workoutId) {
    Workout workout =
        workoutRepository
            .findByIdAndUserId(workoutId, userId)
            .orElseThrow(() -> new NotFoundException("Workout not found."));
    if (workout.getStatus() != WorkoutStatus.ACTIVE) {
      throw new ConflictException("Recommendations can only be applied to active workouts.");
    }
    return workout;
  }

  private record ParsedProposal(
      BigDecimal proposedWeight,
      int proposedSets,
      int proposedRepRangeMin,
      int proposedRepRangeMax,
      int targetRir) {}
}
