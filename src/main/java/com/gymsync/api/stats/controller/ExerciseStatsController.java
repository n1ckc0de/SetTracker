package com.gymsync.api.stats.controller;

import com.gymsync.api.auth.security.UserPrincipal;
import com.gymsync.api.stats.dto.NextSessionRecommendationResponse;
import com.gymsync.api.stats.dto.PersonalRecordResponse;
import com.gymsync.api.stats.dto.PrHistoryResponse;
import com.gymsync.api.stats.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exercises/{exerciseId}")
@Tag(name = "Statistics", description = "Personal records and progressive-overload recommendations")
public class ExerciseStatsController {

  private final StatsService statsService;

  public ExerciseStatsController(StatsService statsService) {
    this.statsService = statsService;
  }

  @GetMapping("/pr")
  @Operation(summary = "Personal records for an exercise, from confirmed completed workouts only")
  public PersonalRecordResponse personalRecord(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID exerciseId) {
    return statsService.personalRecord(principal.getId(), exerciseId);
  }

  @GetMapping("/pr-history")
  @Operation(
      summary = "Estimated 1RM time series for an exercise (one point per completed session)")
  public PrHistoryResponse prHistory(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID exerciseId) {
    return statsService.prHistory(principal.getId(), exerciseId);
  }

  @GetMapping("/next-session-recommendation")
  @Operation(
      summary =
          "Deterministic progressive-overload recommendation based on the most recent session")
  public NextSessionRecommendationResponse nextSessionRecommendation(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID exerciseId) {
    return statsService.nextSessionRecommendation(principal.getId(), exerciseId);
  }
}
