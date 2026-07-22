package com.gymsync.api.workout.domain;

import com.gymsync.api.user.domain.User;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "workouts")
public class Workout {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private WorkoutStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private WorkoutSource source;

  @Enumerated(EnumType.STRING)
  @Column(name = "import_status", nullable = false, length = 50)
  private ImportStatus importStatus;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "raw_import_text")
  private String rawImportText;

  protected Workout() {}

  public Workout(
      User user,
      Instant startedAt,
      WorkoutStatus status,
      WorkoutSource source,
      ImportStatus importStatus) {
    this.user = Objects.requireNonNull(user, "user must not be null");
    this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.importStatus = Objects.requireNonNull(importStatus, "importStatus must not be null");
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public WorkoutStatus getStatus() {
    return status;
  }

  public WorkoutSource getSource() {
    return source;
  }

  public ImportStatus getImportStatus() {
    return importStatus;
  }

  public Long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public String getRawImportText() {
    return rawImportText;
  }

  public void setRawImportText(String rawImportText) {
    this.rawImportText = rawImportText;
  }

  public static Workout createPendingImport(User user, Instant at, String rawText) {
    Workout workout =
        new Workout(
            user,
            at,
            WorkoutStatus.COMPLETED,
            WorkoutSource.NOTES_IMPORT,
            ImportStatus.PENDING_REVIEW);
    workout.endedAt = at;
    workout.rawImportText = rawText;
    return workout;
  }

  public boolean isActive() {
    return status == WorkoutStatus.ACTIVE;
  }

  public void complete(Instant endedAt) {
    if (status != WorkoutStatus.ACTIVE) {
      throw new IllegalStateException("Only active workouts can be completed");
    }
    this.endedAt = Objects.requireNonNull(endedAt, "endedAt must not be null");
    this.status = WorkoutStatus.COMPLETED;
  }

  public void cancel() {
    if (status == WorkoutStatus.COMPLETED) {
      throw new IllegalStateException("Completed workouts cannot be cancelled");
    }
    this.status = WorkoutStatus.CANCELLED;
    if (endedAt == null) {
      endedAt = Instant.now();
    }
  }

  public void confirmImport() {
    if (importStatus != ImportStatus.PENDING_REVIEW) {
      throw new IllegalStateException("Only pending-review imports can be confirmed");
    }
    importStatus = ImportStatus.CONFIRMED;
  }

  public void rejectImport() {
    if (importStatus != ImportStatus.PENDING_REVIEW) {
      throw new IllegalStateException("Only pending-review imports can be rejected");
    }
    importStatus = ImportStatus.REJECTED;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Workout other)) {
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
    return "Workout{id="
        + id
        + ", userId="
        + (user != null ? user.getId() : null)
        + ", status="
        + status
        + ", source="
        + source
        + "}";
  }
}
