package com.gymsync.api.exercise.controller;

import com.gymsync.api.auth.security.UserPrincipal;
import com.gymsync.api.exercise.domain.MediaSuggestionStatus;
import com.gymsync.api.exercise.dto.ExerciseMediaSuggestionResponse;
import com.gymsync.api.exercise.service.ExerciseMediaSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exercises/{exerciseId}/media-suggestions")
@Tag(
    name = "Exercise media suggestions",
    description = "Auto-suggested media pending human review before publishing onto an exercise")
public class ExerciseMediaSuggestionController {

  private final ExerciseMediaSuggestionService suggestionService;

  public ExerciseMediaSuggestionController(ExerciseMediaSuggestionService suggestionService) {
    this.suggestionService = suggestionService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary =
          "Generate pending media suggestions (curated YouTube, Wikimedia, optional YouTube API)")
  public List<ExerciseMediaSuggestionResponse> generate(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID exerciseId) {
    return suggestionService.generate(principal.getId(), exerciseId);
  }

  @GetMapping
  @Operation(summary = "List media suggestions for an exercise (default status=PENDING)")
  public List<ExerciseMediaSuggestionResponse> list(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID exerciseId,
      @RequestParam(required = false, defaultValue = "PENDING") MediaSuggestionStatus status) {
    return suggestionService.list(principal.getId(), exerciseId, status);
  }

  @PatchMapping("/{suggestionId}/confirm")
  @Operation(summary = "Confirm a pending suggestion and publish its media onto the exercise")
  public ExerciseMediaSuggestionResponse confirm(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID exerciseId,
      @PathVariable UUID suggestionId) {
    return suggestionService.confirm(principal.getId(), exerciseId, suggestionId);
  }

  @PatchMapping("/{suggestionId}/reject")
  @Operation(summary = "Reject a pending media suggestion")
  public ExerciseMediaSuggestionResponse reject(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID exerciseId,
      @PathVariable UUID suggestionId) {
    return suggestionService.reject(principal.getId(), exerciseId, suggestionId);
  }
}
