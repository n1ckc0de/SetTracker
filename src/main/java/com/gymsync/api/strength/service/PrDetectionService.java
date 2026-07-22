package com.gymsync.api.strength.service;

import com.gymsync.api.stats.repository.StatsQueryRepository;
import com.gymsync.api.strength.service.Estimated1RmService.EstimateRequest;
import com.gymsync.api.strength.service.Estimated1RmService.EstimateResponse;
import com.gymsync.api.workout.domain.WorkoutSet;
import com.gymsync.api.workout.dto.PrDetectionResult;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * PR detection for completed working sets. Uses normalized total {@code weight}, never raw {@code
 * enteredWeight}. High-rep sets may compute e1RM but never trigger an e1RM PR notice.
 */
@Service
public class PrDetectionService {

  private final StatsQueryRepository statsQueryRepository;
  private final Estimated1RmService estimated1RmService;

  public PrDetectionService(
      StatsQueryRepository statsQueryRepository, Estimated1RmService estimated1RmService) {
    this.statsQueryRepository = statsQueryRepository;
    this.estimated1RmService = estimated1RmService;
  }

  public PrDetectionResult detect(UUID userId, WorkoutSet candidate, UUID excludeSetId) {
    if (!candidate.isCompleted() || candidate.getReps() <= 0) {
      return noPr();
    }
    if (!candidate.getSetType().countsForPr()) {
      return noPr();
    }

    List<WorkoutSet> history =
        statsQueryRepository.findCountableSetsForExercise(userId, candidate.getExercise().getId());
    BigDecimal previousBestWeight =
        history.stream()
            .filter(s -> excludeSetId == null || !s.getId().equals(excludeSetId))
            .filter(PrDetectionService::eligibleForPr)
            .map(WorkoutSet::getWeight)
            .max(Comparator.naturalOrder())
            .orElse(BigDecimal.ZERO);

    boolean weightPr =
        previousBestWeight.compareTo(BigDecimal.ZERO) == 0
            || candidate.getWeight().compareTo(previousBestWeight) > 0;

    boolean e1rmPr = false;
    BigDecimal previousBestE1rm = BigDecimal.ZERO;
    if (candidate.getReps() <= Estimated1RmService.LOW_CONFIDENCE_REP_THRESHOLD) {
      previousBestE1rm =
          history.stream()
              .filter(s -> excludeSetId == null || !s.getId().equals(excludeSetId))
              .filter(PrDetectionService::eligibleForPr)
              .filter(s -> s.getReps() <= Estimated1RmService.LOW_CONFIDENCE_REP_THRESHOLD)
              .map(s -> Estimated1RmService.epley(s.getWeight(), s.getReps()))
              .max(Comparator.naturalOrder())
              .orElse(BigDecimal.ZERO);

      EstimateResponse estimate =
          estimated1RmService.estimate(
              new EstimateRequest(candidate.getWeight(), candidate.getReps(), candidate.getRir()));
      e1rmPr =
          previousBestE1rm.compareTo(BigDecimal.ZERO) == 0
              || estimate.workingEstimate().compareTo(previousBestE1rm) > 0;
    }

    if (!weightPr && !e1rmPr) {
      return noPr();
    }
    return new PrDetectionResult(
        weightPr,
        e1rmPr,
        previousBestWeight.compareTo(BigDecimal.ZERO) == 0 ? null : previousBestWeight,
        previousBestE1rm.compareTo(BigDecimal.ZERO) == 0 ? null : previousBestE1rm);
  }

  private static boolean eligibleForPr(WorkoutSet set) {
    return set.isCompleted() && set.getReps() > 0 && set.getSetType().countsForPr();
  }

  private static PrDetectionResult noPr() {
    return new PrDetectionResult(false, false, null, null);
  }
}
