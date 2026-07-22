package com.gymsync.api.workout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymsync.api.user.domain.Role;
import com.gymsync.api.user.domain.User;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WorkoutTest {

  private final User user = new User("athlete@example.com", "hash", "Athlete", Role.USER);
  private final Instant now = Instant.parse("2026-07-18T12:00:00Z");

  @Test
  void complete_fromActive_setsEndedAtAndCompletedStatus() {
    Workout workout =
        new Workout(
            user, now, WorkoutStatus.ACTIVE, WorkoutSource.MANUAL, ImportStatus.NOT_APPLICABLE);
    Instant endedAt = Instant.parse("2026-07-18T13:00:00Z");

    workout.complete(endedAt);

    assertThat(workout.getStatus()).isEqualTo(WorkoutStatus.COMPLETED);
    assertThat(workout.getEndedAt()).isEqualTo(endedAt);
  }

  @Test
  void complete_whenAlreadyCompleted_throws() {
    Workout workout =
        new Workout(
            user, now, WorkoutStatus.ACTIVE, WorkoutSource.MANUAL, ImportStatus.NOT_APPLICABLE);
    workout.complete(now);

    assertThatThrownBy(() -> workout.complete(now.plusSeconds(60)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only active");
  }

  @Test
  void cancel_fromActive_transitionsToCancelled() {
    Workout workout =
        new Workout(
            user, now, WorkoutStatus.ACTIVE, WorkoutSource.MANUAL, ImportStatus.NOT_APPLICABLE);

    workout.cancel();

    assertThat(workout.getStatus()).isEqualTo(WorkoutStatus.CANCELLED);
    assertThat(workout.getEndedAt()).isNotNull();
  }

  @Test
  void cancel_whenCompleted_throws() {
    Workout workout =
        new Workout(
            user, now, WorkoutStatus.ACTIVE, WorkoutSource.MANUAL, ImportStatus.NOT_APPLICABLE);
    workout.complete(now);

    assertThatThrownBy(workout::cancel)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot be cancelled");
  }

  @Test
  void confirmImport_fromPending_transitionsToConfirmed() {
    Workout workout = Workout.createPendingImport(user, now, "Bench 3x8 135");

    workout.confirmImport();

    assertThat(workout.getImportStatus()).isEqualTo(ImportStatus.CONFIRMED);
    assertThat(workout.getSource()).isEqualTo(WorkoutSource.NOTES_IMPORT);
  }

  @Test
  void rejectImport_fromPending_transitionsToRejected() {
    Workout workout = Workout.createPendingImport(user, now, "Squat 5x5 185");

    workout.rejectImport();

    assertThat(workout.getImportStatus()).isEqualTo(ImportStatus.REJECTED);
  }

  @Test
  void confirmImport_whenAlreadyConfirmed_throws() {
    Workout workout = Workout.createPendingImport(user, now, "Deadlift 1x5 225");
    workout.confirmImport();

    assertThatThrownBy(workout::confirmImport)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("pending-review");
  }

  @Test
  void rejectImport_whenAlreadyRejected_throws() {
    Workout workout = Workout.createPendingImport(user, now, "OHP 3x8 95");
    workout.rejectImport();

    assertThatThrownBy(workout::rejectImport)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("pending-review");
  }
}
