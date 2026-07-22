package com.gymsync.api.workout.domain;

import com.gymsync.api.exercise.domain.Exercise;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "workout_exercise_notes")
public class WorkoutExerciseNote {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "workout_id", nullable = false)
  private Workout workout;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "exercise_id", nullable = false)
  private Exercise exercise;

  @Column(nullable = false, length = 500)
  private String note;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected WorkoutExerciseNote() {}

  public WorkoutExerciseNote(Workout workout, Exercise exercise, String note) {
    this.workout = Objects.requireNonNull(workout);
    this.exercise = Objects.requireNonNull(exercise);
    this.note = Objects.requireNonNull(note, "note").trim();
    if (this.note.isBlank()) {
      throw new IllegalArgumentException("note must not be blank");
    }
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

  public Workout getWorkout() {
    return workout;
  }

  public Exercise getExercise() {
    return exercise;
  }

  public String getNote() {
    return note;
  }

  public void updateNote(String note) {
    String trimmed = Objects.requireNonNull(note, "note").trim();
    if (trimmed.isBlank()) {
      throw new IllegalArgumentException("note must not be blank");
    }
    this.note = trimmed;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
