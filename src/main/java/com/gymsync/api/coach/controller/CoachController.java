package com.gymsync.api.coach.controller;

import com.gymsync.api.auth.security.UserPrincipal;
import com.gymsync.api.coach.domain.RecommendationStatus;
import com.gymsync.api.coach.dto.ApplyCoachRecommendationRequest;
import com.gymsync.api.coach.dto.CoachApplyPreviewResponse;
import com.gymsync.api.coach.dto.CoachChatRequest;
import com.gymsync.api.coach.dto.CoachRecommendationResponse;
import com.gymsync.api.coach.dto.GenerateCoachRequest;
import com.gymsync.api.coach.dto.ModifyCoachRequest;
import com.gymsync.api.coach.service.CoachService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coach")
@Tag(
    name = "AI Coach",
    description =
        "Bounds-checked training recommendations. The coach never modifies workouts directly — "
            + "users must accept, modify, or dismiss proposals.")
public class CoachController {

  private final CoachService coachService;

  public CoachController(CoachService coachService) {
    this.coachService = coachService;
  }

  @GetMapping("/recommendations")
  @Operation(summary = "List coach recommendations, optionally filtered by status")
  public List<CoachRecommendationResponse> list(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) RecommendationStatus status) {
    return coachService.list(principal.getId(), status);
  }

  @PostMapping("/recommendations/generate")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Generate a new proposed recommendation within deterministic bounds",
      description =
          "Uses the latest readiness check-in and recent exercise history. Numeric bounds are "
              + "computed in application code; the LLM (if enabled) only selects within them.")
  public CoachRecommendationResponse generate(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestBody(required = false) GenerateCoachRequest request) {
    return coachService.generate(
        principal.getId(), request == null ? new GenerateCoachRequest(null, null) : request);
  }

  @PatchMapping("/recommendations/{id}/accept")
  @Operation(summary = "Accept a PROPOSED recommendation (does not mutate workout data)")
  public CoachRecommendationResponse accept(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    return coachService.accept(principal.getId(), id);
  }

  @PatchMapping("/recommendations/{id}/modify")
  @Operation(
      summary = "Modify the proposed change on a PROPOSED recommendation, then mark MODIFIED")
  public CoachRecommendationResponse modify(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody ModifyCoachRequest request) {
    return coachService.modify(principal.getId(), id, request);
  }

  @PatchMapping("/recommendations/{id}/dismiss")
  @Operation(summary = "Dismiss a PROPOSED recommendation")
  public CoachRecommendationResponse dismiss(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    return coachService.dismiss(principal.getId(), id);
  }

  @GetMapping("/recommendations/{id}/apply-preview")
  @Operation(summary = "Preview applying a recommendation to uncompleted sets in an active workout")
  public CoachApplyPreviewResponse applyPreview(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @RequestParam UUID workoutId) {
    return coachService.applyPreview(principal.getId(), id, workoutId);
  }

  @PostMapping("/recommendations/{id}/apply")
  @Operation(summary = "Apply a recommendation to uncompleted sets after explicit approval")
  public List<com.gymsync.api.workout.dto.WorkoutSetResponse> applyToSession(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody ApplyCoachRecommendationRequest request) {
    return coachService.applyToSession(principal.getId(), id, request);
  }

  @PostMapping("/chat")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Chat-style check-in that returns a structured CoachRecommendation card",
      description =
          "Parses free text into a readiness check-in, runs the bounds-checked pipeline, and "
              + "returns a recommendation (not a freeform chat reply). Injury/pain keywords "
              + "return a fixed safety response without calling the LLM.")
  public CoachRecommendationResponse chat(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CoachChatRequest request) {
    return coachService.chat(principal.getId(), request);
  }
}
