package com.gymsync.api.stats.controller;

import com.gymsync.api.auth.security.UserPrincipal;
import com.gymsync.api.stats.dto.VolumeTrendResponse;
import com.gymsync.api.stats.dto.WeeklyStatsResponse;
import com.gymsync.api.stats.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@Tag(name = "Statistics", description = "Weekly training volume and progress summaries")
public class StatsController {

  private final StatsService statsService;

  public StatsController(StatsService statsService) {
    this.statsService = statsService;
  }

  @GetMapping("/weekly")
  @Operation(
      summary = "Weekly training statistics (Monday-Sunday) for the week containing the given date",
      description =
          "Counts only confirmed, completed workouts. Defaults to the current week (UTC) "
              + "when `date` is omitted.")
  public WeeklyStatsResponse weeklyStats(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "Any date within the desired week; defaults to today (UTC)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return statsService.weeklyStats(principal.getId(), date);
  }

  @GetMapping("/volume-trend")
  @Operation(summary = "Weekly total volume trend for the last N weeks (default 12, max 52)")
  public VolumeTrendResponse volumeTrend(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(defaultValue = "12") int weeks) {
    return statsService.volumeTrend(principal.getId(), weeks);
  }
}
