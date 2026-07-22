package com.gymsync.api.checkin.service;

import com.gymsync.api.checkin.domain.CheckInEvent;
import com.gymsync.api.checkin.domain.CheckInStatus;
import com.gymsync.api.checkin.dto.CheckInWebhookRequest;
import com.gymsync.api.checkin.dto.CheckInWebhookResponse;
import com.gymsync.api.checkin.repository.CheckInEventRepository;
import com.gymsync.api.common.exception.ConflictException;
import com.gymsync.api.common.exception.NotFoundException;
import com.gymsync.api.common.util.TransactionUtils;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import com.gymsync.api.websocket.service.SessionEventPublisher;
import com.gymsync.api.workout.domain.ImportStatus;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.domain.WorkoutSource;
import com.gymsync.api.workout.domain.WorkoutStatus;
import com.gymsync.api.workout.repository.WorkoutRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Processes signed gym check-in webhooks. Idempotency is enforced by the unique {@code
 * external_event_id} constraint; the one-active-workout rule is enforced by the partial unique
 * index on {@code workouts}. Uniqueness races are recovered in a <em>new</em> transaction because
 * PostgreSQL aborts the current transaction after a constraint violation — querying in the same
 * transaction would raise "current transaction is aborted".
 */
@Service
public class CheckInService {

  private final CheckInEventRepository checkInEventRepository;
  private final UserRepository userRepository;
  private final WorkoutRepository workoutRepository;
  private final SessionEventPublisher sessionEventPublisher;
  private final MeterRegistry meterRegistry;
  private final TransactionTemplate transactionTemplate;

  public CheckInService(
      CheckInEventRepository checkInEventRepository,
      UserRepository userRepository,
      WorkoutRepository workoutRepository,
      SessionEventPublisher sessionEventPublisher,
      MeterRegistry meterRegistry,
      PlatformTransactionManager transactionManager) {
    this.checkInEventRepository = checkInEventRepository;
    this.userRepository = userRepository;
    this.workoutRepository = workoutRepository;
    this.sessionEventPublisher = sessionEventPublisher;
    this.meterRegistry = meterRegistry;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  public CheckInWebhookResponse process(CheckInWebhookRequest request) {
    try {
      return transactionTemplate.execute(status -> processInTransaction(request));
    } catch (DataIntegrityViolationException ex) {
      return recoverFromConstraintRace(request.eventId());
    }
  }

  private CheckInWebhookResponse processInTransaction(CheckInWebhookRequest request) {
    Optional<CheckInEvent> existing =
        checkInEventRepository.findByExternalEventId(request.eventId());
    if (existing.isPresent()) {
      return toDuplicateResponse(existing.get());
    }

    User user =
        userRepository
            .findById(request.userId())
            .orElseThrow(
                () -> {
                  meterRegistry
                      .counter("gymsync.webhook.checkin", "result", "unknown_user")
                      .increment();
                  return new NotFoundException("User not found.");
                });

    Instant receivedAt = Instant.now();
    CheckInEvent event =
        new CheckInEvent(
            request.eventId(), user, request.source(), receivedAt, CheckInStatus.RECEIVED);

    Optional<Workout> active = workoutRepository.findByUserAndStatus(user, WorkoutStatus.ACTIVE);
    if (active.isPresent()) {
      event.markRejected("User already has an active workout.");
      try {
        checkInEventRepository.saveAndFlush(event);
      } catch (DataIntegrityViolationException ex) {
        // Propagate so the outer process() recovers in a fresh transaction.
        throw ex;
      }
      meterRegistry.counter("gymsync.webhook.checkin", "result", "rejected_active").increment();
      throw new ConflictException("User already has an active workout.");
    }

    Instant startedAt = request.occurredAt() != null ? request.occurredAt() : receivedAt;
    Workout workout =
        new Workout(
            user,
            startedAt,
            WorkoutStatus.ACTIVE,
            WorkoutSource.WEBHOOK,
            ImportStatus.NOT_APPLICABLE);
    workoutRepository.saveAndFlush(workout);
    event.markProcessed(workout);
    checkInEventRepository.saveAndFlush(event);

    UUID workoutId = workout.getId();
    UUID userId = user.getId();
    TransactionUtils.runAfterCommit(
        () -> sessionEventPublisher.publishSessionStarted(workoutId, userId));
    meterRegistry.counter("gymsync.webhook.checkin", "result", "accepted").increment();
    return new CheckInWebhookResponse(
        event.getId(),
        event.getExternalEventId(),
        CheckInStatus.PROCESSED,
        workoutId,
        "Check-in processed; workout started.");
  }

  /**
   * After a uniqueness race, PostgreSQL has aborted the original transaction. Look up the winning
   * event in a new transaction; if the race was on the active-workout index instead, surface a
   * conflict.
   */
  private CheckInWebhookResponse recoverFromConstraintRace(String eventId) {
    // Brief retries: the winning peer may still be inserting the check-in event after it
    // created the workout.
    for (int attempt = 0; attempt < 5; attempt++) {
      CheckInWebhookResponse duplicate =
          transactionTemplate.execute(
              status -> {
                Optional<CheckInEvent> raced =
                    checkInEventRepository.findByExternalEventId(eventId);
                return raced.map(this::toDuplicateResponse).orElse(null);
              });
      if (duplicate != null) {
        return duplicate;
      }
      try {
        Thread.sleep(25L);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    meterRegistry.counter("gymsync.webhook.checkin", "result", "rejected_active").increment();
    throw new ConflictException("User already has an active workout.");
  }

  private CheckInWebhookResponse toDuplicateResponse(CheckInEvent event) {
    meterRegistry.counter("gymsync.webhook.checkin", "result", "duplicate").increment();
    UUID workoutId =
        event.getTriggeredWorkout() != null ? event.getTriggeredWorkout().getId() : null;
    return new CheckInWebhookResponse(
        event.getId(),
        event.getExternalEventId(),
        CheckInStatus.DUPLICATE,
        workoutId,
        "Duplicate event ignored.");
  }
}
