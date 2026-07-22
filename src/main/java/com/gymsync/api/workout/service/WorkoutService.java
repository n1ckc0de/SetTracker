package com.gymsync.api.workout.service;

import com.gymsync.api.common.exception.ConflictException;
import com.gymsync.api.common.exception.NotFoundException;
import com.gymsync.api.common.response.PageResponse;
import com.gymsync.api.common.util.TransactionUtils;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import com.gymsync.api.websocket.service.SessionEventPublisher;
import com.gymsync.api.workout.domain.ImportStatus;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.domain.WorkoutSource;
import com.gymsync.api.workout.domain.WorkoutStatus;
import com.gymsync.api.workout.dto.WorkoutDetailResponse;
import com.gymsync.api.workout.dto.WorkoutResponse;
import com.gymsync.api.workout.dto.WorkoutSetResponse;
import com.gymsync.api.workout.repository.WorkoutRepository;
import com.gymsync.api.workout.repository.WorkoutSetRepository;
import com.gymsync.api.workout.repository.WorkoutSpecifications;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workout lifecycle: start (manual), list/filter history, view detail, complete, and cancel.
 *
 * <p><b>One active workout per user:</b> enforced both here (a friendly pre-check) and by the
 * database (unique partial index {@code idx_workouts_one_active_per_user} on {@code
 * workouts(user_id) WHERE status = 'ACTIVE'}). The pre-check makes the common case return a clean
 * {@link ConflictException} without hitting the database's constraint; the constraint is what
 * actually prevents two concurrent requests from both creating an active workout (see
 * docs/DECISIONS.md and docs/CONCURRENCY.md).
 */
@Service
public class WorkoutService {

  private static final String ACTIVE_WORKOUT_CONFLICT_MESSAGE =
      "You already have an active workout. Complete or cancel it before starting a new one.";
  private static final String NOT_FOUND_MESSAGE = "Workout not found.";

  private final WorkoutRepository workoutRepository;
  private final WorkoutSetRepository workoutSetRepository;
  private final UserRepository userRepository;
  private final SessionEventPublisher sessionEventPublisher;

  public WorkoutService(
      WorkoutRepository workoutRepository,
      WorkoutSetRepository workoutSetRepository,
      UserRepository userRepository,
      SessionEventPublisher sessionEventPublisher) {
    this.workoutRepository = workoutRepository;
    this.workoutSetRepository = workoutSetRepository;
    this.userRepository = userRepository;
    this.sessionEventPublisher = sessionEventPublisher;
  }

  @Transactional
  public WorkoutResponse startWorkout(UUID userId) {
    User user = userRepository.getReferenceById(userId);
    if (workoutRepository.findByUserAndStatus(user, WorkoutStatus.ACTIVE).isPresent()) {
      throw new ConflictException(ACTIVE_WORKOUT_CONFLICT_MESSAGE);
    }

    Workout workout =
        new Workout(
            user,
            Instant.now(),
            WorkoutStatus.ACTIVE,
            WorkoutSource.MANUAL,
            ImportStatus.NOT_APPLICABLE);
    try {
      workoutRepository.saveAndFlush(workout);
    } catch (DataIntegrityViolationException ex) {
      // Race: a concurrent request already created (and committed) the active workout.
      throw new ConflictException(ACTIVE_WORKOUT_CONFLICT_MESSAGE);
    }

    WorkoutResponse response = WorkoutResponse.from(workout);
    UUID workoutId = workout.getId();
    TransactionUtils.runAfterCommit(
        () -> sessionEventPublisher.publishSessionStarted(workoutId, userId));
    return response;
  }

  @Transactional(readOnly = true)
  public PageResponse<WorkoutResponse> listWorkouts(
      UUID userId,
      WorkoutStatus status,
      WorkoutSource source,
      LocalDate from,
      LocalDate to,
      Pageable pageable) {
    User user = userRepository.getReferenceById(userId);
    Instant fromInstant = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant toInstant =
        to == null ? null : to.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC).toInstant();
    Specification<Workout> spec =
        Specification.allOf(
            WorkoutSpecifications.forUser(user),
            WorkoutSpecifications.hasStatus(status),
            WorkoutSpecifications.hasSource(source),
            WorkoutSpecifications.startedAtFrom(fromInstant),
            WorkoutSpecifications.startedAtTo(toInstant));
    Page<Workout> page = workoutRepository.findAll(spec, pageable);
    return PageResponse.from(page, WorkoutResponse::from);
  }

  @Transactional(readOnly = true)
  public WorkoutDetailResponse getWorkout(UUID userId, UUID workoutId) {
    Workout workout = requireOwnedWorkout(userId, workoutId);
    return buildDetail(workout);
  }

  @Transactional
  public WorkoutDetailResponse completeWorkout(UUID userId, UUID workoutId) {
    Workout workout = requireOwnedWorkout(userId, workoutId);
    if (workout.getStatus() != WorkoutStatus.ACTIVE) {
      throw new ConflictException("Only active workouts can be completed.");
    }

    workout.complete(Instant.now());
    try {
      workoutRepository.saveAndFlush(workout);
    } catch (ObjectOptimisticLockingFailureException ex) {
      throw new ConflictException("This workout was modified concurrently. Please retry.");
    }

    WorkoutDetailResponse response = buildDetail(workout);
    UUID completedId = workout.getId();
    TransactionUtils.runAfterCommit(
        () -> sessionEventPublisher.publishSessionCompleted(completedId, userId));
    return response;
  }

  /**
   * "Deletes" a workout by soft-cancelling it ({@code status = CANCELLED}) rather than removing the
   * row. Historical integrity matters here: cancelled workouts still carry useful audit information
   * (e.g. a check-in that mistakenly started a session), and hard deletion would orphan or
   * cascade-delete any sets already logged. See docs/DECISIONS.md ("Soft-cancel instead of hard
   * delete for workouts").
   */
  @Transactional
  public void cancelWorkout(UUID userId, UUID workoutId) {
    Workout workout = requireOwnedWorkout(userId, workoutId);
    if (workout.getStatus() == WorkoutStatus.CANCELLED) {
      return;
    }
    if (workout.getStatus() == WorkoutStatus.COMPLETED) {
      throw new ConflictException("Completed workouts cannot be cancelled.");
    }
    workout.cancel();
    workoutRepository.save(workout);
  }

  private Workout requireOwnedWorkout(UUID userId, UUID workoutId) {
    return workoutRepository
        .findByIdAndUserId(workoutId, userId)
        .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE));
  }

  private WorkoutDetailResponse buildDetail(Workout workout) {
    List<WorkoutSetResponse> sets =
        workoutSetRepository.findByWorkoutFetchExercise(workout).stream()
            .map(WorkoutSetResponse::from)
            .toList();
    return WorkoutDetailResponse.from(workout, sets);
  }
}
