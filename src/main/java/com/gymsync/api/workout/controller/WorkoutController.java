package com.gymsync.api.workout.controller;

import com.gymsync.api.auth.security.UserPrincipal;
import com.gymsync.api.common.response.PageResponse;
import com.gymsync.api.notesimport.service.NotesImportService;
import com.gymsync.api.workout.domain.WorkoutSource;
import com.gymsync.api.workout.domain.WorkoutStatus;
import com.gymsync.api.workout.dto.WorkoutDetailResponse;
import com.gymsync.api.workout.dto.WorkoutResponse;
import com.gymsync.api.workout.service.WorkoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workouts")
@Tag(name = "Workouts", description = "Workout sessions: start, list/filter, complete, cancel")
public class WorkoutController {

  private final WorkoutService workoutService;
  private final NotesImportService notesImportService;

  public WorkoutController(WorkoutService workoutService, NotesImportService notesImportService) {
    this.workoutService = workoutService;
    this.notesImportService = notesImportService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Start a new manual, active workout (one active workout per user)")
  public WorkoutResponse startWorkout(@AuthenticationPrincipal UserPrincipal principal) {
    return workoutService.startWorkout(principal.getId());
  }

  @GetMapping
  @Operation(summary = "List the current user's workout history, newest first by default")
  public PageResponse<WorkoutResponse> listWorkouts(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) WorkoutStatus status,
      @RequestParam(required = false) WorkoutSource source,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return workoutService.listWorkouts(principal.getId(), status, source, from, to, pageable);
  }

  @GetMapping("/{workoutId}")
  @Operation(summary = "Get a workout with its sets and exercise info")
  public WorkoutDetailResponse getWorkout(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID workoutId) {
    return workoutService.getWorkout(principal.getId(), workoutId);
  }

  @PatchMapping("/{workoutId}/complete")
  @Operation(summary = "Complete an active workout")
  public WorkoutDetailResponse completeWorkout(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID workoutId) {
    return workoutService.completeWorkout(principal.getId(), workoutId);
  }

  /**
   * Cancels (soft-deletes) a workout: sets {@code status = CANCELLED} rather than removing the row,
   * preserving history/audit integrity. See docs/DECISIONS.md.
   */
  @DeleteMapping("/{workoutId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Cancel a workout (soft delete; completed workouts cannot be cancelled)")
  public void cancelWorkout(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID workoutId) {
    workoutService.cancelWorkout(principal.getId(), workoutId);
  }

  @PatchMapping("/{workoutId}/confirm-import")
  @Operation(
      summary = "Confirm an AI-imported pending-review workout so it counts toward statistics")
  public WorkoutDetailResponse confirmImport(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID workoutId) {
    return notesImportService.confirmImport(principal.getId(), workoutId);
  }

  @PatchMapping("/{workoutId}/reject-import")
  @Operation(summary = "Reject an AI-imported pending-review workout")
  public WorkoutDetailResponse rejectImport(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID workoutId) {
    return notesImportService.rejectImport(principal.getId(), workoutId);
  }
}
