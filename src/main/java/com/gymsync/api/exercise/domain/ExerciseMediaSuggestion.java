package com.gymsync.api.exercise.domain;

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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "exercise_media_suggestions")
public class ExerciseMediaSuggestion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "exercise_id", nullable = false)
  private Exercise exercise;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requested_by_user_id")
  private User requestedBy;

  @Column(name = "thumbnail_url", length = 2048)
  private String thumbnailUrl;

  @Column(name = "demo_video_url", length = 2048)
  private String demoVideoUrl;

  @Column(name = "media_attribution", nullable = false, length = 500)
  private String mediaAttribution;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private MediaSuggestionSource source;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private MediaSuggestionConfidence confidence;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private MediaSuggestionStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  protected ExerciseMediaSuggestion() {}

  public ExerciseMediaSuggestion(
      Exercise exercise,
      User requestedBy,
      String thumbnailUrl,
      String demoVideoUrl,
      String mediaAttribution,
      MediaSuggestionSource source,
      MediaSuggestionConfidence confidence) {
    this.exercise = Objects.requireNonNull(exercise, "exercise must not be null");
    this.requestedBy = requestedBy;
    this.thumbnailUrl = thumbnailUrl;
    this.demoVideoUrl = demoVideoUrl;
    this.mediaAttribution =
        Objects.requireNonNull(mediaAttribution, "mediaAttribution must not be null");
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.confidence = Objects.requireNonNull(confidence, "confidence must not be null");
    this.status = MediaSuggestionStatus.PENDING;
  }

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public Exercise getExercise() {
    return exercise;
  }

  public User getRequestedBy() {
    return requestedBy;
  }

  public String getThumbnailUrl() {
    return thumbnailUrl;
  }

  public String getDemoVideoUrl() {
    return demoVideoUrl;
  }

  public String getMediaAttribution() {
    return mediaAttribution;
  }

  public MediaSuggestionSource getSource() {
    return source;
  }

  public MediaSuggestionConfidence getConfidence() {
    return confidence;
  }

  public MediaSuggestionStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public void confirm() {
    this.status = MediaSuggestionStatus.CONFIRMED;
    this.reviewedAt = Instant.now();
  }

  public void reject() {
    this.status = MediaSuggestionStatus.REJECTED;
    this.reviewedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ExerciseMediaSuggestion other)) {
      return false;
    }
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
