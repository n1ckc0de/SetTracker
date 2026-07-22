package com.gymsync.api.exercise.repository;

import com.gymsync.api.exercise.domain.ExerciseMediaSuggestion;
import com.gymsync.api.exercise.domain.MediaSuggestionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseMediaSuggestionRepository
    extends JpaRepository<ExerciseMediaSuggestion, UUID> {

  List<ExerciseMediaSuggestion> findByExerciseIdAndStatusOrderByCreatedAtDesc(
      UUID exerciseId, MediaSuggestionStatus status);

  List<ExerciseMediaSuggestion> findByExerciseIdAndStatus(
      UUID exerciseId, MediaSuggestionStatus status);

  Optional<ExerciseMediaSuggestion> findByIdAndExerciseId(UUID id, UUID exerciseId);
}
