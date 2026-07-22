package com.gymsync.api.workout.service;

import com.gymsync.api.common.exception.ConflictException;
import com.gymsync.api.common.exception.NotFoundException;
import com.gymsync.api.common.util.TransactionUtils;
import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.exercise.repository.ExerciseRepository;
import com.gymsync.api.strength.service.PrDetectionService;
import com.gymsync.api.websocket.service.SessionEventPublisher;
import com.gymsync.api.workout.domain.ImportStatus;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.domain.WorkoutExerciseNote;
import com.gymsync.api.workout.domain.WorkoutSet;
import com.gymsync.api.workout.domain.WorkoutStatus;
import com.gymsync.api.workout.dto.CreateBatchWorkoutSetsRequest;
import com.gymsync.api.workout.dto.CreateWorkoutSetRequest;
import com.gymsync.api.workout.dto.UpdateWorkoutSetRequest;
import com.gymsync.api.workout.dto.UpsertWorkoutExerciseNoteRequest;
import com.gymsync.api.workout.dto.WorkoutExerciseNoteResponse;
import com.gymsync.api.workout.dto.WorkoutSetResponse;
import com.gymsync.api.workout.repository.WorkoutExerciseNoteRepository;
import com.gymsync.api.workout.repository.WorkoutRepository;
import com.gymsync.api.workout.repository.WorkoutSetRepository;
import com.gymsync.api.workout.service.WorkoutSetRequestResolver.ResolvedEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workout-set CRUD.
 *
 * <p><b>Set-number uniqueness:</b> the same {@code (workout, exercise, setNumber)} combination
 * cannot be logged twice. This is enforced by the database's unique constraint {@code
 * workout_sets_unique_set}; the service pre-checks it for a clean {@link ConflictException} and
 * also catches {@link DataIntegrityViolationException} to convert a race into the same 409 response
 * instead of a 500. See docs/DECISIONS.md.
 */
@Service
public class WorkoutSetService {

  private static final String WORKOUT_NOT_FOUND_MESSAGE = "Workout not found.";
  private static final String SET_NOT_FOUND_MESSAGE = "Set not found.";
  private static final String EXERCISE_NOT_FOUND_MESSAGE = "Exercise not found.";
  private static final String DUPLICATE_SET_MESSAGE =
      "A set with this set number already exists for this exercise in this workout.";
  private static final String NOT_MUTABLE_MESSAGE =
      "Sets can only be added to active workouts or imports pending review.";

  private final WorkoutRepository workoutRepository;
  private final WorkoutSetRepository workoutSetRepository;
  private final WorkoutExerciseNoteRepository workoutExerciseNoteRepository;
  private final ExerciseRepository exerciseRepository;
  private final SessionEventPublisher sessionEventPublisher;
  private final PrDetectionService prDetectionService;

  public WorkoutSetService(
      WorkoutRepository workoutRepository,
      WorkoutSetRepository workoutSetRepository,
      WorkoutExerciseNoteRepository workoutExerciseNoteRepository,
      ExerciseRepository exerciseRepository,
      SessionEventPublisher sessionEventPublisher,
      PrDetectionService prDetectionService) {
    this.workoutRepository = workoutRepository;
    this.workoutSetRepository = workoutSetRepository;
    this.workoutExerciseNoteRepository = workoutExerciseNoteRepository;
    this.exerciseRepository = exerciseRepository;
    this.sessionEventPublisher = sessionEventPublisher;
    this.prDetectionService = prDetectionService;
  }

  @Transactional
  public WorkoutSetResponse addSet(UUID userId, UUID workoutId, CreateWorkoutSetRequest request) {
    Workout workout = requireOwnedWorkout(userId, workoutId);
    assertMutable(workout);
    Exercise exercise = requireVisibleExercise(userId, request.exerciseId());
    ResolvedEntry entry = WorkoutSetRequestResolver.resolve(request);

    if (workoutSetRepository.existsByWorkoutAndExerciseAndSetNumber(
        workout, exercise, request.setNumber())) {
      throw new ConflictException(DUPLICATE_SET_MESSAGE);
    }

    Instant loggedAt = request.loggedAt() != null ? request.loggedAt() : Instant.now();
    WorkoutSet set =
        createSet(workout, exercise, request.reps(), request.setNumber(), loggedAt, entry);
    saveSet(set);

    var pr = set.isCompleted() ? prDetectionService.detect(userId, set, set.getId()) : null;
    return WorkoutSetResponse.from(set, hasPr(pr) ? pr : null);
  }

  @Transactional
  public List<WorkoutSetResponse> addBatchSets(
      UUID userId, UUID workoutId, CreateBatchWorkoutSetsRequest request) {
    Workout workout = requireOwnedWorkout(userId, workoutId);
    assertMutable(workout);
    Exercise exercise = requireVisibleExercise(userId, request.exerciseId());
    ResolvedEntry entry = WorkoutSetRequestResolver.resolve(request);

    int start =
        request.startingSetNumber() != null
            ? request.startingSetNumber()
            : nextSetNumber(workout, exercise);
    List<WorkoutSetResponse> created = new ArrayList<>();
    Instant loggedAt = Instant.now();
    for (int i = 0; i < request.count(); i++) {
      int setNumber = start + i;
      if (workoutSetRepository.existsByWorkoutAndExerciseAndSetNumber(
          workout, exercise, setNumber)) {
        throw new ConflictException(DUPLICATE_SET_MESSAGE);
      }
      WorkoutSet set = createSet(workout, exercise, request.reps(), setNumber, loggedAt, entry);
      saveSet(set);
      created.add(WorkoutSetResponse.from(set));
    }
    return created;
  }

  @Transactional(readOnly = true)
  public List<WorkoutSetResponse> listSets(UUID userId, UUID workoutId) {
    Workout workout = requireOwnedWorkout(userId, workoutId);
    return workoutSetRepository.findByWorkoutFetchExercise(workout).stream()
        .map(WorkoutSetResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<WorkoutExerciseNoteResponse> listExerciseNotes(UUID userId, UUID workoutId) {
    Workout workout = requireOwnedWorkout(userId, workoutId);
    return workoutExerciseNoteRepository.findByWorkout(workout).stream()
        .map(n -> new WorkoutExerciseNoteResponse(n.getExercise().getId(), n.getNote()))
        .toList();
  }

  @Transactional
  public WorkoutExerciseNoteResponse upsertExerciseNote(
      UUID userId, UUID workoutId, UUID exerciseId, UpsertWorkoutExerciseNoteRequest request) {
    Workout workout = requireOwnedWorkout(userId, workoutId);
    assertMutable(workout);
    Exercise exercise = requireVisibleExercise(userId, exerciseId);
    WorkoutExerciseNote note =
        workoutExerciseNoteRepository
            .findByWorkoutAndExercise(workout, exercise)
            .orElseGet(() -> new WorkoutExerciseNote(workout, exercise, request.note()));
    if (note.getId() != null) {
      note.updateNote(request.note());
    }
    return new WorkoutExerciseNoteResponse(
        exercise.getId(), workoutExerciseNoteRepository.save(note).getNote());
  }

  @Transactional
  public WorkoutSetResponse updateSet(UUID userId, UUID setId, UpdateWorkoutSetRequest request) {
    WorkoutSet set = requireOwnedSet(userId, setId);
    Workout workout = set.getWorkout();
    assertMutable(workout);

    if (!request.setNumber().equals(set.getSetNumber())
        && workoutSetRepository.existsByWorkoutAndExerciseAndSetNumberAndIdNot(
            workout, set.getExercise(), request.setNumber(), set.getId())) {
      throw new ConflictException(DUPLICATE_SET_MESSAGE);
    }

    if (request.weightEntryMode() == com.gymsync.api.workout.domain.WeightEntryMode.PER_SIDE
        && request.barWeight() == null) {
      throw new ConflictException("barWeight is required when weightEntryMode is PER_SIDE.");
    }

    boolean wasCompleted = set.isCompleted();
    set.update(
        request.enteredWeight(),
        request.weightEntryMode(),
        request.barWeight(),
        request.reps(),
        request.setNumber(),
        request.setType(),
        request.rir(),
        request.note(),
        request.completed());
    saveSet(set);

    saveSet(set);

    var pr =
        !wasCompleted && set.isCompleted()
            ? prDetectionService.detect(userId, set, set.getId())
            : null;
    publishUpdated(set);
    return WorkoutSetResponse.from(set, hasPr(pr) ? pr : null);
  }

  @Transactional
  public WorkoutSetResponse completeSet(UUID userId, UUID setId) {
    WorkoutSet set = requireOwnedSet(userId, setId);
    Workout workout = set.getWorkout();
    assertMutable(workout);
    if (set.isCompleted()) {
      return WorkoutSetResponse.from(set, null);
    }
    set.markCompleted();
    saveSet(set);
    var pr = prDetectionService.detect(userId, set, set.getId());
    publishUpdated(set);
    return WorkoutSetResponse.from(set, hasPr(pr) ? pr : null);
  }

  @Transactional
  public void deleteSet(UUID userId, UUID setId) {
    WorkoutSet set = requireOwnedSet(userId, setId);
    Workout workout = set.getWorkout();
    assertMutable(workout);

    workoutSetRepository.delete(set);
    UUID workoutId = workout.getId();
    TransactionUtils.runAfterCommit(
        () -> sessionEventPublisher.publishSetDeleted(workoutId, setId));
  }

  private WorkoutSet createSet(
      Workout workout,
      Exercise exercise,
      int reps,
      int setNumber,
      Instant loggedAt,
      ResolvedEntry entry) {
    return new WorkoutSet(
        workout,
        exercise,
        null,
        reps,
        setNumber,
        loggedAt,
        entry.setType(),
        entry.rir(),
        entry.weightEntryMode(),
        entry.barWeight(),
        entry.enteredWeight(),
        entry.note(),
        entry.completed());
  }

  private void saveSet(WorkoutSet set) {
    try {
      workoutSetRepository.saveAndFlush(set);
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException(DUPLICATE_SET_MESSAGE);
    }
    UUID workoutId = set.getWorkout().getId();
    UUID setId = set.getId();
    TransactionUtils.runAfterCommit(() -> sessionEventPublisher.publishSetLogged(workoutId, setId));
  }

  private void publishUpdated(WorkoutSet set) {
    UUID workoutId = set.getWorkout().getId();
    UUID setId = set.getId();
    TransactionUtils.runAfterCommit(
        () -> sessionEventPublisher.publishSetUpdated(workoutId, setId));
  }

  private static boolean hasPr(com.gymsync.api.workout.dto.PrDetectionResult pr) {
    return pr != null && (pr.weightPr() || pr.estimatedOneRepMaxPr());
  }

  private int nextSetNumber(Workout workout, Exercise exercise) {
    return workoutSetRepository.findByWorkoutFetchExercise(workout).stream()
            .filter(s -> s.getExercise().getId().equals(exercise.getId()))
            .mapToInt(WorkoutSet::getSetNumber)
            .max()
            .orElse(0)
        + 1;
  }

  private void assertMutable(Workout workout) {
    boolean active = workout.getStatus() == WorkoutStatus.ACTIVE;
    boolean pendingImport = workout.getImportStatus() == ImportStatus.PENDING_REVIEW;
    if (!active && !pendingImport) {
      throw new ConflictException(NOT_MUTABLE_MESSAGE);
    }
  }

  private Workout requireOwnedWorkout(UUID userId, UUID workoutId) {
    return workoutRepository
        .findByIdAndUserId(workoutId, userId)
        .orElseThrow(() -> new NotFoundException(WORKOUT_NOT_FOUND_MESSAGE));
  }

  private WorkoutSet requireOwnedSet(UUID userId, UUID setId) {
    WorkoutSet set =
        workoutSetRepository
            .findByIdFetchWorkoutAndExercise(setId)
            .orElseThrow(() -> new NotFoundException(SET_NOT_FOUND_MESSAGE));
    if (!set.getWorkout().getUser().getId().equals(userId)) {
      throw new NotFoundException(SET_NOT_FOUND_MESSAGE);
    }
    return set;
  }

  private Exercise requireVisibleExercise(UUID userId, UUID exerciseId) {
    Exercise exercise =
        exerciseRepository
            .findById(exerciseId)
            .orElseThrow(() -> new NotFoundException(EXERCISE_NOT_FOUND_MESSAGE));
    boolean visible = exercise.isGlobal() || exercise.getCreatedBy().getId().equals(userId);
    if (!visible) {
      throw new NotFoundException(EXERCISE_NOT_FOUND_MESSAGE);
    }
    return exercise;
  }
}
