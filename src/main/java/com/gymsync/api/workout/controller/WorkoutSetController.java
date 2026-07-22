package com.gymsync.api.workout.controller;

import com.gymsync.api.auth.security.UserPrincipal;
import com.gymsync.api.workout.dto.CreateBatchWorkoutSetsRequest;
import com.gymsync.api.workout.dto.CreateWorkoutSetRequest;
import com.gymsync.api.workout.dto.UpdateWorkoutSetRequest;
import com.gymsync.api.workout.dto.UpsertWorkoutExerciseNoteRequest;
import com.gymsync.api.workout.dto.WorkoutExerciseNoteResponse;
import com.gymsync.api.workout.dto.WorkoutSetResponse;
import com.gymsync.api.workout.service.WorkoutSetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sets are logged/edited/removed under the owning workout's id, except that update/delete-by-id use
 * the flatter {@code /api/sets/{setId}} form (spec section 9), since a set id is already globally
 * unique and callers editing/removing a single set don't need to also know its workout.
 */
@RestController
@Tag(name = "Workout Sets", description = "Logging, editing, and removing sets within a workout")
public class WorkoutSetController {

  private final WorkoutSetService workoutSetService;

  public WorkoutSetController(WorkoutSetService workoutSetService) {
    this.workoutSetService = workoutSetService;
  }

  @PostMapping("/api/workouts/{workoutId}/sets")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Log a set on an active workout or a pending-review import")
  public WorkoutSetResponse addSet(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID workoutId,
      @Valid @RequestBody CreateWorkoutSetRequest request) {
    return workoutSetService.addSet(principal.getId(), workoutId, request);
  }

  @PostMapping("/api/workouts/{workoutId}/sets/batch")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Log multiple sets with shared values at once")
  public List<WorkoutSetResponse> addBatchSets(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID workoutId,
      @Valid @RequestBody CreateBatchWorkoutSetsRequest request) {
    return workoutSetService.addBatchSets(principal.getId(), workoutId, request);
  }

  @GetMapping("/api/workouts/{workoutId}/sets")
  @Operation(summary = "List the sets logged for a workout")
  public List<WorkoutSetResponse> listSets(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID workoutId) {
    return workoutSetService.listSets(principal.getId(), workoutId);
  }

  @GetMapping("/api/workouts/{workoutId}/exercise-notes")
  @Operation(summary = "List exercise-level notes for a workout")
  public List<WorkoutExerciseNoteResponse> listExerciseNotes(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID workoutId) {
    return workoutSetService.listExerciseNotes(principal.getId(), workoutId);
  }

  @PutMapping("/api/workouts/{workoutId}/exercises/{exerciseId}/note")
  @Operation(summary = "Create or update an exercise-level note for a workout")
  public WorkoutExerciseNoteResponse upsertExerciseNote(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID workoutId,
      @PathVariable UUID exerciseId,
      @Valid @RequestBody UpsertWorkoutExerciseNoteRequest request) {
    return workoutSetService.upsertExerciseNote(principal.getId(), workoutId, exerciseId, request);
  }

  @PutMapping("/api/sets/{setId}")
  @Operation(summary = "Update a logged set")
  public WorkoutSetResponse updateSet(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID setId,
      @Valid @RequestBody UpdateWorkoutSetRequest request) {
    return workoutSetService.updateSet(principal.getId(), setId, request);
  }

  @PatchMapping("/api/sets/{setId}/complete")
  @Operation(summary = "Mark a set complete and evaluate PRs")
  public WorkoutSetResponse completeSet(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID setId) {
    return workoutSetService.completeSet(principal.getId(), setId);
  }

  @DeleteMapping("/api/sets/{setId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a logged set")
  public void deleteSet(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID setId) {
    workoutSetService.deleteSet(principal.getId(), setId);
  }
}
