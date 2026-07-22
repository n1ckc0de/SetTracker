package com.gymsync.api.checkin.domain;

import com.gymsync.api.user.domain.User;
import com.gymsync.api.workout.domain.Workout;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "check_in_events")
public class CheckInEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "external_event_id", nullable = false, unique = true, length = 255)
  private String externalEventId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 255)
  private String source;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "triggered_workout_id")
  private Workout triggeredWorkout;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private CheckInStatus status;

  @Column(name = "failure_reason")
  private String failureReason;

  protected CheckInEvent() {}

  public CheckInEvent(
      String externalEventId, User user, String source, Instant receivedAt, CheckInStatus status) {
    this.externalEventId =
        Objects.requireNonNull(externalEventId, "externalEventId must not be null");
    this.user = Objects.requireNonNull(user, "user must not be null");
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
    this.status = Objects.requireNonNull(status, "status must not be null");
  }

  public UUID getId() {
    return id;
  }

  public String getExternalEventId() {
    return externalEventId;
  }

  public User getUser() {
    return user;
  }

  public String getSource() {
    return source;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public Workout getTriggeredWorkout() {
    return triggeredWorkout;
  }

  public CheckInStatus getStatus() {
    return status;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void markProcessed(Workout workout) {
    this.status = CheckInStatus.PROCESSED;
    this.triggeredWorkout = workout;
    this.failureReason = null;
  }

  public void markDuplicate() {
    this.status = CheckInStatus.DUPLICATE;
  }

  public void markRejected(String reason) {
    this.status = CheckInStatus.REJECTED;
    this.failureReason = reason;
  }

  public void markFailed(String reason) {
    this.status = CheckInStatus.FAILED;
    this.failureReason = reason;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CheckInEvent other)) {
      return false;
    }
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  @Override
  public String toString() {
    return "CheckInEvent{id="
        + id
        + ", externalEventId='"
        + externalEventId
        + "', status="
        + status
        + "}";
  }
}
