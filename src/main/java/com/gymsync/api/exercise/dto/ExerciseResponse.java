package com.gymsync.api.exercise.dto;

import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.exercise.domain.ExerciseCategory;
import java.time.Instant;
import java.util.UUID;

public record ExerciseResponse(
    UUID id,
    String name,
    ExerciseCategory category,
    boolean global,
    boolean owned,
    String thumbnailUrl,
    String demoVideoUrl,
    String mediaAttribution,
    Instant createdAt,
    Instant updatedAt) {

  public static ExerciseResponse from(Exercise exercise, UUID currentUserId) {
    boolean global = exercise.isGlobal();
    boolean owned = !global && exercise.getCreatedBy().getId().equals(currentUserId);
    return new ExerciseResponse(
        exercise.getId(),
        exercise.getName(),
        exercise.getCategory(),
        global,
        owned,
        exercise.getThumbnailUrl(),
        exercise.getDemoVideoUrl(),
        exercise.getMediaAttribution(),
        exercise.getCreatedAt(),
        exercise.getUpdatedAt());
  }
}
