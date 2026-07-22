package com.gymsync.api.workout.repository;

import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.domain.WorkoutExerciseNote;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutExerciseNoteRepository extends JpaRepository<WorkoutExerciseNote, UUID> {

  Optional<WorkoutExerciseNote> findByWorkoutAndExercise(Workout workout, Exercise exercise);

  List<WorkoutExerciseNote> findByWorkout(Workout workout);
}
