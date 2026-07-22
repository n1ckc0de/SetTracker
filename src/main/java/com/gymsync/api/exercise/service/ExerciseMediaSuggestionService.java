package com.gymsync.api.exercise.service;

import com.gymsync.api.common.exception.ConflictException;
import com.gymsync.api.common.exception.NotFoundException;
import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.exercise.domain.ExerciseMediaSuggestion;
import com.gymsync.api.exercise.domain.MediaSuggestionConfidence;
import com.gymsync.api.exercise.domain.MediaSuggestionSource;
import com.gymsync.api.exercise.domain.MediaSuggestionStatus;
import com.gymsync.api.exercise.dto.ExerciseMediaSuggestionResponse;
import com.gymsync.api.exercise.media.CuratedYouTubeDemoCatalog;
import com.gymsync.api.exercise.media.MediaSearchClient;
import com.gymsync.api.exercise.repository.ExerciseMediaSuggestionRepository;
import com.gymsync.api.exercise.repository.ExerciseRepository;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ExerciseMediaSuggestionService {

  private static final String NOT_FOUND_MESSAGE = "Exercise not found.";
  private static final String SUGGESTION_NOT_FOUND = "Media suggestion not found.";
  private static final String GLOBAL_MEDIA_CONFLICT =
      "Global exercise already has published media. Confirm is only allowed when media fields are"
          + " empty.";

  private final ExerciseRepository exerciseRepository;
  private final ExerciseMediaSuggestionRepository suggestionRepository;
  private final UserRepository userRepository;
  private final MediaSearchClient mediaSearchClient;

  public ExerciseMediaSuggestionService(
      ExerciseRepository exerciseRepository,
      ExerciseMediaSuggestionRepository suggestionRepository,
      UserRepository userRepository,
      MediaSearchClient mediaSearchClient) {
    this.exerciseRepository = exerciseRepository;
    this.suggestionRepository = suggestionRepository;
    this.userRepository = userRepository;
    this.mediaSearchClient = mediaSearchClient;
  }

  @Transactional
  public List<ExerciseMediaSuggestionResponse> generate(UUID userId, UUID exerciseId) {
    Exercise exercise = findVisible(userId, exerciseId);
    User requester = userRepository.getReferenceById(userId);

    rejectPriorPending(exerciseId);

    List<ExerciseMediaSuggestion> created = new ArrayList<>();

    CuratedYouTubeDemoCatalog.findByExerciseName(exercise.getName())
        .ifPresent(
            curated ->
                created.add(
                    saveSuggestion(
                        exercise,
                        requester,
                        null,
                        curated.demoVideoUrl(),
                        curated.mediaAttribution(),
                        MediaSuggestionSource.CURATED,
                        MediaSuggestionConfidence.HIGH)));

    mediaSearchClient
        .findWikimediaImage(exercise.getName())
        .ifPresent(
            image -> {
              String attribution = image.title() + " via Wikimedia Commons";
              created.add(
                  saveSuggestion(
                      exercise,
                      requester,
                      image.imageUrl(),
                      null,
                      attribution,
                      MediaSuggestionSource.WIKIMEDIA,
                      MediaSuggestionConfidence.MEDIUM));
            });

    mediaSearchClient
        .findYouTubeDemo(exercise.getName())
        .ifPresent(
            video -> {
              String attribution =
                  "Suggested YouTube result \""
                      + video.title()
                      + "\" (pending review). Embed-only; content remains on YouTube.";
              created.add(
                  saveSuggestion(
                      exercise,
                      requester,
                      null,
                      video.videoUrl(),
                      attribution,
                      MediaSuggestionSource.YOUTUBE_API,
                      MediaSuggestionConfidence.MEDIUM));
            });

    return created.stream().map(ExerciseMediaSuggestionResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<ExerciseMediaSuggestionResponse> list(
      UUID userId, UUID exerciseId, MediaSuggestionStatus status) {
    findVisible(userId, exerciseId);
    MediaSuggestionStatus filter = status == null ? MediaSuggestionStatus.PENDING : status;
    return suggestionRepository
        .findByExerciseIdAndStatusOrderByCreatedAtDesc(exerciseId, filter)
        .stream()
        .map(ExerciseMediaSuggestionResponse::from)
        .toList();
  }

  @Transactional
  public ExerciseMediaSuggestionResponse confirm(UUID userId, UUID exerciseId, UUID suggestionId) {
    Exercise exercise = findVisible(userId, exerciseId);
    ExerciseMediaSuggestion suggestion = findPendingSuggestion(exerciseId, suggestionId);

    if (exercise.isGlobal()) {
      if (hasPublishedMedia(exercise)) {
        throw new ConflictException(GLOBAL_MEDIA_CONFLICT);
      }
    } else if (!isOwnedBy(exercise, userId)) {
      throw new NotFoundException(NOT_FOUND_MESSAGE);
    }

    String thumbnail = firstNonBlank(suggestion.getThumbnailUrl(), exercise.getThumbnailUrl());
    String demo = firstNonBlank(suggestion.getDemoVideoUrl(), exercise.getDemoVideoUrl());
    String attribution =
        firstNonBlank(suggestion.getMediaAttribution(), exercise.getMediaAttribution());

    ExerciseMediaValidator.validate(thumbnail, demo, attribution);
    exercise.updateMedia(
        ExerciseMediaValidator.blankToNull(thumbnail),
        ExerciseMediaValidator.blankToNull(demo),
        ExerciseMediaValidator.blankToNull(attribution));
    suggestion.confirm();
    return ExerciseMediaSuggestionResponse.from(suggestion);
  }

  @Transactional
  public ExerciseMediaSuggestionResponse reject(UUID userId, UUID exerciseId, UUID suggestionId) {
    findVisible(userId, exerciseId);
    ExerciseMediaSuggestion suggestion = findPendingSuggestion(exerciseId, suggestionId);
    suggestion.reject();
    return ExerciseMediaSuggestionResponse.from(suggestion);
  }

  private void rejectPriorPending(UUID exerciseId) {
    List<ExerciseMediaSuggestion> prior =
        suggestionRepository.findByExerciseIdAndStatus(exerciseId, MediaSuggestionStatus.PENDING);
    for (ExerciseMediaSuggestion suggestion : prior) {
      suggestion.reject();
    }
  }

  private ExerciseMediaSuggestion saveSuggestion(
      Exercise exercise,
      User requester,
      String thumbnailUrl,
      String demoVideoUrl,
      String mediaAttribution,
      MediaSuggestionSource source,
      MediaSuggestionConfidence confidence) {
    ExerciseMediaSuggestion suggestion =
        new ExerciseMediaSuggestion(
            exercise,
            requester,
            ExerciseMediaValidator.blankToNull(thumbnailUrl),
            ExerciseMediaValidator.blankToNull(demoVideoUrl),
            truncateAttribution(mediaAttribution),
            source,
            confidence);
    return suggestionRepository.save(suggestion);
  }

  private ExerciseMediaSuggestion findPendingSuggestion(UUID exerciseId, UUID suggestionId) {
    ExerciseMediaSuggestion suggestion =
        suggestionRepository
            .findByIdAndExerciseId(suggestionId, exerciseId)
            .orElseThrow(() -> new NotFoundException(SUGGESTION_NOT_FOUND));
    if (suggestion.getStatus() != MediaSuggestionStatus.PENDING) {
      throw new ConflictException("Only PENDING suggestions can be reviewed.");
    }
    return suggestion;
  }

  private Exercise findVisible(UUID userId, UUID exerciseId) {
    Exercise exercise =
        exerciseRepository
            .findById(exerciseId)
            .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE));
    if (!isVisibleTo(exercise, userId)) {
      throw new NotFoundException(NOT_FOUND_MESSAGE);
    }
    return exercise;
  }

  private static boolean isVisibleTo(Exercise exercise, UUID userId) {
    return exercise.isGlobal() || exercise.getCreatedBy().getId().equals(userId);
  }

  private static boolean isOwnedBy(Exercise exercise, UUID userId) {
    return !exercise.isGlobal() && exercise.getCreatedBy().getId().equals(userId);
  }

  private static boolean hasPublishedMedia(Exercise exercise) {
    return StringUtils.hasText(exercise.getThumbnailUrl())
        || StringUtils.hasText(exercise.getDemoVideoUrl())
        || StringUtils.hasText(exercise.getMediaAttribution());
  }

  private static String truncateAttribution(String attribution) {
    if (attribution == null) {
      return null;
    }
    String trimmed = attribution.trim();
    if (trimmed.length() <= 500) {
      return trimmed;
    }
    return trimmed.substring(0, 497) + "...";
  }

  private static String firstNonBlank(String primary, String fallback) {
    if (StringUtils.hasText(primary)) {
      return primary.trim();
    }
    if (StringUtils.hasText(fallback)) {
      return fallback.trim();
    }
    return null;
  }
}
