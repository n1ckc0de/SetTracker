package com.gymsync.api.readiness.domain;

import com.gymsync.api.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "readiness_check_ins")
public class ReadinessCheckIn {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "workout_id")
  private UUID workoutId;

  @Column(name = "sleep_hours", precision = 4, scale = 2)
  private BigDecimal sleepHours;

  @Column(name = "sleep_quality")
  private Integer sleepQuality;

  @Column(name = "energy_level")
  private Integer energyLevel;

  private Integer soreness;

  private Integer stress;

  @Column(length = 500)
  private String note;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ReadinessCheckIn() {}

  public ReadinessCheckIn(User user) {
    this.user = Objects.requireNonNull(user, "user");
  }

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public UUID getWorkoutId() {
    return workoutId;
  }

  public void setWorkoutId(UUID workoutId) {
    this.workoutId = workoutId;
  }

  public BigDecimal getSleepHours() {
    return sleepHours;
  }

  public void setSleepHours(BigDecimal sleepHours) {
    this.sleepHours = sleepHours;
  }

  public Integer getSleepQuality() {
    return sleepQuality;
  }

  public void setSleepQuality(Integer sleepQuality) {
    this.sleepQuality = sleepQuality;
  }

  public Integer getEnergyLevel() {
    return energyLevel;
  }

  public void setEnergyLevel(Integer energyLevel) {
    this.energyLevel = energyLevel;
  }

  public Integer getSoreness() {
    return soreness;
  }

  public void setSoreness(Integer soreness) {
    this.soreness = soreness;
  }

  public Integer getStress() {
    return stress;
  }

  public void setStress(Integer stress) {
    this.stress = stress;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
