package com.gymsync.api.exercise.service;

import com.gymsync.api.common.exception.ConflictException;
import com.gymsync.api.common.exception.NotFoundException;
import com.gymsync.api.common.response.PageResponse;
import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.exercise.domain.ExerciseCategory;
import com.gymsync.api.exercise.dto.CreateExerciseRequest;
import com.gymsync.api.exercise.dto.ExerciseResponse;
import com.gymsync.api.exercise.dto.UpdateExerciseRequest;
import com.gymsync.api.exercise.repository.ExerciseRepository;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Exercise CRUD with visibility/ownership rules from spec section 4:
 *
 * <ul>
 *   <li>Every user can see global exercises ({@code createdBy == null}) plus their own custom ones.
 *   <li>Only the creator may update or delete a custom exercise.
 *   <li>Direct ID access to another user's custom exercise (or an attempt to edit a global
 *       exercise, which nobody "owns") is reported as {@code 404}, not {@code 403}, so we never
 *       confirm that a given id belongs to someone else.
 * </ul>
 */
@Service
public class ExerciseService {

  private static final String NOT_FOUND_MESSAGE = "Exercise not found.";

  private final ExerciseRepository exerciseRepository;
  private final UserRepository userRepository;

  public ExerciseService(ExerciseRepository exerciseRepository, UserRepository userRepository) {
    this.exerciseRepository = exerciseRepository;
    this.userRepository = userRepository;
  }

  @Transactional(readOnly = true)
  public PageResponse<ExerciseResponse> listExercises(
      UUID userId, String query, ExerciseCategory category, Pageable pageable) {
    User user = userRepository.getReferenceById(userId);
    String normalizedQuery = normalizeQuery(query);
    Page<Exercise> page =
        exerciseRepository.findVisibleExercises(user, normalizedQuery, category, pageable);
    return PageResponse.from(page, exercise -> ExerciseResponse.from(exercise, userId));
  }

  @Transactional(readOnly = true)
  public ExerciseResponse getExercise(UUID userId, UUID exerciseId) {
    Exercise exercise = findVisible(userId, exerciseId);
    return ExerciseResponse.from(exercise, userId);
  }

  @Transactional
  public ExerciseResponse createExercise(UUID userId, CreateExerciseRequest request) {
    User user = userRepository.getReferenceById(userId);
    String name = request.name().trim();
    String normalizedName = ExerciseNameNormalizer.normalize(name);
    ExerciseMediaValidator.validate(
        request.thumbnailUrl(), request.demoVideoUrl(), request.mediaAttribution());
    Exercise exercise = new Exercise(name, normalizedName, request.category(), user);
    exercise.updateMedia(
        ExerciseMediaValidator.blankToNull(request.thumbnailUrl()),
        ExerciseMediaValidator.blankToNull(request.demoVideoUrl()),
        ExerciseMediaValidator.blankToNull(request.mediaAttribution()));
    exerciseRepository.save(exercise);
    return ExerciseResponse.from(exercise, userId);
  }

  @Transactional
  public ExerciseResponse updateExercise(
      UUID userId, UUID exerciseId, UpdateExerciseRequest request) {
    Exercise exercise = findOwned(userId, exerciseId);
    String name = request.name().trim();
    ExerciseMediaValidator.validate(
        request.thumbnailUrl(), request.demoVideoUrl(), request.mediaAttribution());
    exercise.rename(name, ExerciseNameNormalizer.normalize(name));
    exercise.changeCategory(request.category());
    exercise.updateMedia(
        ExerciseMediaValidator.blankToNull(request.thumbnailUrl()),
        ExerciseMediaValidator.blankToNull(request.demoVideoUrl()),
        ExerciseMediaValidator.blankToNull(request.mediaAttribution()));
    return ExerciseResponse.from(exercise, userId);
  }

  @Transactional
  public void deleteExercise(UUID userId, UUID exerciseId) {
    Exercise exercise = findOwned(userId, exerciseId);
    try {
      exerciseRepository.delete(exercise);
      exerciseRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException(
          "This exercise cannot be deleted because it is referenced by existing workout sets.");
    }
  }

  private String normalizeQuery(String query) {
    if (!StringUtils.hasText(query)) {
      return null;
    }
    return ExerciseNameNormalizer.normalize(query);
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

  private Exercise findOwned(UUID userId, UUID exerciseId) {
    Exercise exercise =
        exerciseRepository
            .findById(exerciseId)
            .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE));
    if (!isOwnedBy(exercise, userId)) {
      throw new NotFoundException(NOT_FOUND_MESSAGE);
    }
    return exercise;
  }

  private boolean isVisibleTo(Exercise exercise, UUID userId) {
    return exercise.isGlobal() || exercise.getCreatedBy().getId().equals(userId);
  }

  private boolean isOwnedBy(Exercise exercise, UUID userId) {
    return !exercise.isGlobal() && exercise.getCreatedBy().getId().equals(userId);
  }
}
