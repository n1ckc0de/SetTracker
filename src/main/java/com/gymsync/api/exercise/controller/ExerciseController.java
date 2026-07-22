package com.gymsync.api.exercise.controller;

import com.gymsync.api.auth.security.UserPrincipal;
import com.gymsync.api.common.response.PageResponse;
import com.gymsync.api.exercise.domain.ExerciseCategory;
import com.gymsync.api.exercise.dto.CreateExerciseRequest;
import com.gymsync.api.exercise.dto.ExerciseDetailResponse;
import com.gymsync.api.exercise.dto.ExerciseResponse;
import com.gymsync.api.exercise.dto.UpdateExerciseRequest;
import com.gymsync.api.exercise.service.ExerciseDetailService;
import com.gymsync.api.exercise.service.ExerciseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exercises")
@Tag(name = "Exercises", description = "Global and user-owned exercise catalog")
public class ExerciseController {

  private final ExerciseService exerciseService;
  private final ExerciseDetailService exerciseDetailService;

  public ExerciseController(
      ExerciseService exerciseService, ExerciseDetailService exerciseDetailService) {
    this.exerciseService = exerciseService;
    this.exerciseDetailService = exerciseDetailService;
  }

  @GetMapping
  @Operation(
      summary =
          "List exercises visible to the current user (global + own), with search/filter/sort")
  public PageResponse<ExerciseResponse> listExercises(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) ExerciseCategory category,
      @PageableDefault(size = 20, sort = "name") Pageable pageable) {
    return exerciseService.listExercises(principal.getId(), query, category, pageable);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a custom exercise owned by the current user")
  public ExerciseResponse createExercise(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CreateExerciseRequest request) {
    return exerciseService.createExercise(principal.getId(), request);
  }

  @GetMapping("/{exerciseId}")
  @Operation(summary = "Get a single exercise (global, or owned by the current user)")
  public ExerciseResponse getExercise(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID exerciseId) {
    return exerciseService.getExercise(principal.getId(), exerciseId);
  }

  @GetMapping("/{exerciseId}/detail")
  @Operation(
      summary =
          "Exercise detail: metadata, recent sets, current PR, 1RM trend, and active coach card")
  public ExerciseDetailResponse getExerciseDetail(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID exerciseId) {
    return exerciseDetailService.detail(principal.getId(), exerciseId);
  }

  @PutMapping("/{exerciseId}")
  @Operation(summary = "Update a custom exercise owned by the current user")
  public ExerciseResponse updateExercise(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID exerciseId,
      @Valid @RequestBody UpdateExerciseRequest request) {
    return exerciseService.updateExercise(principal.getId(), exerciseId, request);
  }

  @DeleteMapping("/{exerciseId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a custom exercise owned by the current user")
  public void deleteExercise(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID exerciseId) {
    exerciseService.deleteExercise(principal.getId(), exerciseId);
  }
}
