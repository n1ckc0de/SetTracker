package com.gymsync.api.exercise.dto;

import com.gymsync.api.exercise.domain.ExerciseMediaSuggestion;
import com.gymsync.api.exercise.domain.MediaSuggestionConfidence;
import com.gymsync.api.exercise.domain.MediaSuggestionSource;
import com.gymsync.api.exercise.domain.MediaSuggestionStatus;
import java.time.Instant;
import java.util.UUID;

public record ExerciseMediaSuggestionResponse(
    UUID id,
    UUID exerciseId,
    String thumbnailUrl,
    String demoVideoUrl,
    String mediaAttribution,
    MediaSuggestionSource source,
    MediaSuggestionConfidence confidence,
    MediaSuggestionStatus status,
    Instant createdAt,
    Instant reviewedAt) {

  public static ExerciseMediaSuggestionResponse from(ExerciseMediaSuggestion suggestion) {
    return new ExerciseMediaSuggestionResponse(
        suggestion.getId(),
        suggestion.getExercise().getId(),
        suggestion.getThumbnailUrl(),
        suggestion.getDemoVideoUrl(),
        suggestion.getMediaAttribution(),
        suggestion.getSource(),
        suggestion.getConfidence(),
        suggestion.getStatus(),
        suggestion.getCreatedAt(),
        suggestion.getReviewedAt());
  }
}
