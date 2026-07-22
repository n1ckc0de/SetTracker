package com.gymsync.api.notesimport.service;

import com.gymsync.api.common.exception.BadRequestException;
import com.gymsync.api.common.exception.ConflictException;
import com.gymsync.api.common.exception.NotFoundException;
import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.exercise.domain.ExerciseCategory;
import com.gymsync.api.exercise.repository.ExerciseRepository;
import com.gymsync.api.exercise.service.ExerciseNameNormalizer;
import com.gymsync.api.notesimport.config.LlmProperties;
import com.gymsync.api.notesimport.dto.ImportNotesRequest;
import com.gymsync.api.notesimport.llm.ParsedSet;
import com.gymsync.api.notesimport.llm.ParsedWorkout;
import com.gymsync.api.notesimport.llm.WorkoutNotesParser;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.domain.WorkoutSet;
import com.gymsync.api.workout.domain.WorkoutSource;
import com.gymsync.api.workout.dto.WorkoutDetailResponse;
import com.gymsync.api.workout.dto.WorkoutSetResponse;
import com.gymsync.api.workout.repository.WorkoutRepository;
import com.gymsync.api.workout.repository.WorkoutSetRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotesImportService {

  private final WorkoutNotesParser workoutNotesParser;
  private final LlmProperties llmProperties;
  private final UserRepository userRepository;
  private final ExerciseRepository exerciseRepository;
  private final WorkoutRepository workoutRepository;
  private final WorkoutSetRepository workoutSetRepository;

  public NotesImportService(
      WorkoutNotesParser workoutNotesParser,
      LlmProperties llmProperties,
      UserRepository userRepository,
      ExerciseRepository exerciseRepository,
      WorkoutRepository workoutRepository,
      WorkoutSetRepository workoutSetRepository) {
    this.workoutNotesParser = workoutNotesParser;
    this.llmProperties = llmProperties;
    this.userRepository = userRepository;
    this.exerciseRepository = exerciseRepository;
    this.workoutRepository = workoutRepository;
    this.workoutSetRepository = workoutSetRepository;
  }

  @Transactional
  public WorkoutDetailResponse importNotes(UUID userId, ImportNotesRequest request) {
    if (request.rawText().length() > llmProperties.getMaxRawTextLength()) {
      throw new BadRequestException("rawText exceeds configured maximum length.");
    }
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found."));
    ParsedWorkout parsed = workoutNotesParser.parse(request.rawText());

    Instant now = Instant.now();
    Workout workout = Workout.createPendingImport(user, now, request.rawText());
    workoutRepository.save(workout);

    List<WorkoutSet> createdSets = new ArrayList<>();
    Map<String, Exercise> resolved = new HashMap<>();
    for (ParsedSet parsedSet : parsed.sets()) {
      Exercise exercise =
          resolved.computeIfAbsent(
              ExerciseNameNormalizer.normalize(parsedSet.exerciseName()),
              key -> resolveExercise(user, parsedSet.exerciseName()));
      WorkoutSet set =
          new WorkoutSet(
              workout, exercise, parsedSet.weight(), parsedSet.reps(), parsedSet.setNumber(), now);
      createdSets.add(set);
    }
    workoutSetRepository.saveAll(createdSets);

    List<WorkoutSetResponse> setResponses =
        workoutSetRepository.findByWorkoutFetchExercise(workout).stream()
            .map(WorkoutSetResponse::from)
            .toList();
    return WorkoutDetailResponse.from(workout, setResponses);
  }

  @Transactional
  public WorkoutDetailResponse confirmImport(UUID userId, UUID workoutId) {
    Workout workout = requireOwnedImport(userId, workoutId);
    try {
      workout.confirmImport();
      workoutRepository.saveAndFlush(workout);
    } catch (IllegalStateException ex) {
      throw new ConflictException(ex.getMessage());
    } catch (ObjectOptimisticLockingFailureException ex) {
      throw new ConflictException("Import was modified concurrently. Please retry.");
    }
    return detail(workout);
  }

  @Transactional
  public WorkoutDetailResponse rejectImport(UUID userId, UUID workoutId) {
    Workout workout = requireOwnedImport(userId, workoutId);
    try {
      workout.rejectImport();
      workoutRepository.saveAndFlush(workout);
    } catch (IllegalStateException ex) {
      throw new ConflictException(ex.getMessage());
    } catch (ObjectOptimisticLockingFailureException ex) {
      throw new ConflictException("Import was modified concurrently. Please retry.");
    }
    return detail(workout);
  }

  private Workout requireOwnedImport(UUID userId, UUID workoutId) {
    Workout workout =
        workoutRepository
            .findByIdAndUserId(workoutId, userId)
            .orElseThrow(() -> new NotFoundException("Workout not found."));
    if (workout.getSource() != WorkoutSource.NOTES_IMPORT) {
      throw new ConflictException("Workout is not an imported notes workout.");
    }
    return workout;
  }

  private WorkoutDetailResponse detail(Workout workout) {
    List<WorkoutSetResponse> sets =
        workoutSetRepository.findByWorkoutFetchExercise(workout).stream()
            .map(WorkoutSetResponse::from)
            .toList();
    return WorkoutDetailResponse.from(workout, sets);
  }

  private Exercise resolveExercise(User user, String rawName) {
    String normalized = ExerciseNameNormalizer.normalize(rawName);
    List<Exercise> matches = exerciseRepository.findVisibleByNormalizedName(user, normalized);
    if (matches.size() == 1) {
      return matches.getFirst();
    }
    if (matches.size() > 1) {
      throw new BadRequestException(
          "Ambiguous exercise '"
              + rawName
              + "'. Candidates: "
              + matches.stream().map(Exercise::getName).toList());
    }
    // No match: create user-owned custom exercise (category OTHER).
    Exercise created = new Exercise(rawName.trim(), normalized, ExerciseCategory.OTHER, user);
    return exerciseRepository.save(created);
  }
}
