package com.gymsync.api.workout.domain;

import com.gymsync.api.exercise.domain.Exercise;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "workout_sets")
public class WorkoutSet {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "workout_id", nullable = false)
  private Workout workout;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "exercise_id", nullable = false)
  private Exercise exercise;

  /** Normalized total load — used by volume/stats/PR queries. */
  @Column(nullable = false, precision = 8, scale = 2)
  private BigDecimal weight;

  @Column(nullable = false)
  private Integer reps;

  @Column(name = "set_number", nullable = false)
  private Integer setNumber;

  @Column(name = "logged_at", nullable = false)
  private Instant loggedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "set_type", nullable = false, length = 20)
  private SetType setType = SetType.WORKING;

  @Column(name = "rir")
  private Integer rir;

  @Enumerated(EnumType.STRING)
  @Column(name = "weight_entry_mode", nullable = false, length = 20)
  private WeightEntryMode weightEntryMode = WeightEntryMode.TOTAL;

  @Column(name = "bar_weight", precision = 8, scale = 2)
  private BigDecimal barWeight;

  @Column(name = "entered_weight", nullable = false, precision = 8, scale = 2)
  private BigDecimal enteredWeight;

  @Column(length = 280)
  private String note;

  @Column(nullable = false)
  private boolean completed = true;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected WorkoutSet() {}

  /** Legacy constructor — TOTAL mode, enteredWeight = weight, completed. */
  public WorkoutSet(
      Workout workout,
      Exercise exercise,
      BigDecimal weight,
      Integer reps,
      Integer setNumber,
      Instant loggedAt) {
    this(
        workout,
        exercise,
        weight,
        reps,
        setNumber,
        loggedAt,
        SetType.WORKING,
        null,
        WeightEntryMode.TOTAL,
        null,
        weight,
        null,
        true);
  }

  public WorkoutSet(
      Workout workout,
      Exercise exercise,
      BigDecimal weight,
      Integer reps,
      Integer setNumber,
      Instant loggedAt,
      SetType setType,
      Integer rir,
      WeightEntryMode weightEntryMode,
      BigDecimal barWeight,
      BigDecimal enteredWeight,
      String note,
      boolean completed) {
    this.workout = Objects.requireNonNull(workout, "workout must not be null");
    this.exercise = Objects.requireNonNull(exercise, "exercise must not be null");
    this.reps = Objects.requireNonNull(reps, "reps must not be null");
    this.setNumber = Objects.requireNonNull(setNumber, "setNumber must not be null");
    this.loggedAt = Objects.requireNonNull(loggedAt, "loggedAt must not be null");
    this.setType = setType != null ? setType : SetType.WORKING;
    this.rir = rir;
    this.weightEntryMode = weightEntryMode != null ? weightEntryMode : WeightEntryMode.TOTAL;
    this.barWeight = barWeight;
    this.enteredWeight = Objects.requireNonNull(enteredWeight, "enteredWeight must not be null");
    this.note = note;
    this.completed = completed;
    this.weight =
        SetWeightNormalizer.computeTotalWeight(
            this.enteredWeight, this.weightEntryMode, this.barWeight);
    validateValues();
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

  private void validateValues() {
    if (weight.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("weight must be non-negative");
    }
    if (reps <= 0) {
      throw new IllegalArgumentException("reps must be positive");
    }
    if (setNumber <= 0) {
      throw new IllegalArgumentException("setNumber must be positive");
    }
    if (rir != null && (rir < 0 || rir > 5)) {
      throw new IllegalArgumentException("rir must be between 0 and 5");
    }
    if (weightEntryMode == WeightEntryMode.PER_SIDE && barWeight == null) {
      throw new IllegalArgumentException("barWeight is required for PER_SIDE entry mode");
    }
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

  public BigDecimal getWeight() {
    return weight;
  }

  public Integer getReps() {
    return reps;
  }

  public Integer getSetNumber() {
    return setNumber;
  }

  public Instant getLoggedAt() {
    return loggedAt;
  }

  public SetType getSetType() {
    return setType;
  }

  public Integer getRir() {
    return rir;
  }

  public WeightEntryMode getWeightEntryMode() {
    return weightEntryMode;
  }

  public BigDecimal getBarWeight() {
    return barWeight;
  }

  public BigDecimal getEnteredWeight() {
    return enteredWeight;
  }

  public String getNote() {
    return note;
  }

  public boolean isCompleted() {
    return completed;
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

  public void update(
      BigDecimal enteredWeight,
      WeightEntryMode weightEntryMode,
      BigDecimal barWeight,
      Integer reps,
      Integer setNumber,
      SetType setType,
      Integer rir,
      String note,
      Boolean completed) {
    this.enteredWeight = Objects.requireNonNull(enteredWeight, "enteredWeight must not be null");
    this.weightEntryMode = weightEntryMode != null ? weightEntryMode : WeightEntryMode.TOTAL;
    this.barWeight = this.weightEntryMode == WeightEntryMode.PER_SIDE ? barWeight : null;
    this.weight =
        SetWeightNormalizer.computeTotalWeight(
            this.enteredWeight, this.weightEntryMode, this.barWeight);
    this.reps = Objects.requireNonNull(reps, "reps must not be null");
    this.setNumber = Objects.requireNonNull(setNumber, "setNumber must not be null");
    if (setType != null) {
      this.setType = setType;
    }
    this.rir = rir;
    this.note = note;
    if (completed != null) {
      this.completed = completed;
    }
    validateValues();
  }

  /** Legacy update — TOTAL mode. */
  public void update(BigDecimal weight, Integer reps, Integer setNumber, Instant loggedAt) {
    this.loggedAt = Objects.requireNonNull(loggedAt, "loggedAt must not be null");
    update(weight, WeightEntryMode.TOTAL, null, reps, setNumber, null, null, null, null);
  }

  public void markCompleted() {
    this.completed = true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof WorkoutSet other)) {
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
    return "WorkoutSet{id="
        + id
        + ", workoutId="
        + (workout != null ? workout.getId() : null)
        + ", exerciseId="
        + (exercise != null ? exercise.getId() : null)
        + ", setNumber="
        + setNumber
        + "}";
  }
}
