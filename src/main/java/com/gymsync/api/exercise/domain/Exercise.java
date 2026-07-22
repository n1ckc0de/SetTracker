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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "exercises")
public class Exercise {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "normalized_name", nullable = false, length = 255)
  private String normalizedName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private ExerciseCategory category;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by")
  private User createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "thumbnail_url", length = 2048)
  private String thumbnailUrl;

  @Column(name = "demo_video_url", length = 2048)
  private String demoVideoUrl;

  @Column(name = "media_attribution", length = 500)
  private String mediaAttribution;

  protected Exercise() {}

  public Exercise(String name, String normalizedName, ExerciseCategory category, User createdBy) {
    this.name = Objects.requireNonNull(name, "name must not be null");
    this.normalizedName = Objects.requireNonNull(normalizedName, "normalizedName must not be null");
    this.category = Objects.requireNonNull(category, "category must not be null");
    this.createdBy = createdBy;
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

  public String getName() {
    return name;
  }

  public String getNormalizedName() {
    return normalizedName;
  }

  public ExerciseCategory getCategory() {
    return category;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public boolean isGlobal() {
    return createdBy == null;
  }

  public void rename(String name, String normalizedName) {
    this.name = Objects.requireNonNull(name, "name must not be null");
    this.normalizedName = Objects.requireNonNull(normalizedName, "normalizedName must not be null");
  }

  public void changeCategory(ExerciseCategory category) {
    this.category = Objects.requireNonNull(category, "category must not be null");
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

  public void updateMedia(String thumbnailUrl, String demoVideoUrl, String mediaAttribution) {
    this.thumbnailUrl = thumbnailUrl;
    this.demoVideoUrl = demoVideoUrl;
    this.mediaAttribution = mediaAttribution;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Exercise other)) {
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
    return "Exercise{id="
        + id
        + ", name='"
        + name
        + "', category="
        + category
        + ", global="
        + isGlobal()
        + "}";
  }
}
