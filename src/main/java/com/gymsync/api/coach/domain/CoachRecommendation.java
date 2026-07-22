package com.gymsync.api.coach.domain;

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
@Table(name = "coach_recommendations")
public class CoachRecommendation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "exercise_id")
  private UUID exerciseId;

  @Column(name = "based_on_workout_id")
  private UUID basedOnWorkoutId;

  @Column(name = "readiness_check_in_id")
  private UUID readinessCheckInId;

  @Enumerated(EnumType.STRING)
  @Column(name = "recommendation_type", nullable = false, length = 50)
  private RecommendationType recommendationType;

  @Column(name = "previous_performance", nullable = false, columnDefinition = "TEXT")
  private String previousPerformance;

  @Column(name = "proposed_change", nullable = false, columnDefinition = "TEXT")
  private String proposedChange;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String reasoning;

  @Enumerated(EnumType.STRING)
  @Column(name = "confidence_level", nullable = false, length = 20)
  private ConfidenceLevel confidenceLevel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RecommendationStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "responded_at")
  private Instant respondedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected CoachRecommendation() {}

  public CoachRecommendation(
      User user,
      UUID exerciseId,
      UUID basedOnWorkoutId,
      UUID readinessCheckInId,
      RecommendationType recommendationType,
      String previousPerformance,
      String proposedChange,
      String reasoning,
      ConfidenceLevel confidenceLevel) {
    this.user = Objects.requireNonNull(user);
    this.exerciseId = exerciseId;
    this.basedOnWorkoutId = basedOnWorkoutId;
    this.readinessCheckInId = readinessCheckInId;
    this.recommendationType = Objects.requireNonNull(recommendationType);
    this.previousPerformance = Objects.requireNonNull(previousPerformance);
    this.proposedChange = Objects.requireNonNull(proposedChange);
    this.reasoning = Objects.requireNonNull(reasoning);
    this.confidenceLevel = Objects.requireNonNull(confidenceLevel);
    this.status = RecommendationStatus.PROPOSED;
  }

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }

  @PreUpdate
  void onUpdate() {
    // version handled by JPA
  }

  public void accept() {
    requireProposed();
    status = RecommendationStatus.ACCEPTED;
    respondedAt = Instant.now();
  }

  public void dismiss() {
    requireProposed();
    status = RecommendationStatus.DISMISSED;
    respondedAt = Instant.now();
  }

  public void modify(String newProposedChange, String newReasoning) {
    requireProposed();
    this.proposedChange = Objects.requireNonNull(newProposedChange);
    if (newReasoning != null && !newReasoning.isBlank()) {
      this.reasoning = newReasoning.trim();
    }
    status = RecommendationStatus.MODIFIED;
    respondedAt = Instant.now();
  }

  private void requireProposed() {
    if (status != RecommendationStatus.PROPOSED) {
      throw new IllegalStateException("Only PROPOSED recommendations can be updated");
    }
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public UUID getExerciseId() {
    return exerciseId;
  }

  public UUID getBasedOnWorkoutId() {
    return basedOnWorkoutId;
  }

  public UUID getReadinessCheckInId() {
    return readinessCheckInId;
  }

  public RecommendationType getRecommendationType() {
    return recommendationType;
  }

  public String getPreviousPerformance() {
    return previousPerformance;
  }

  public String getProposedChange() {
    return proposedChange;
  }

  public String getReasoning() {
    return reasoning;
  }

  public ConfidenceLevel getConfidenceLevel() {
    return confidenceLevel;
  }

  public RecommendationStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getRespondedAt() {
    return respondedAt;
  }

  public Long getVersion() {
    return version;
  }
}
