package com.gymsync.api.program.service;

import com.gymsync.api.common.exception.BadRequestException;
import com.gymsync.api.common.exception.ConflictException;
import com.gymsync.api.common.exception.NotFoundException;
import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.exercise.repository.ExerciseRepository;
import com.gymsync.api.program.domain.Program;
import com.gymsync.api.program.domain.ProgramDay;
import com.gymsync.api.program.domain.ProgramDayExercise;
import com.gymsync.api.program.dto.CreateProgramDayRequest;
import com.gymsync.api.program.dto.CreateProgramRequest;
import com.gymsync.api.program.dto.ProgramDayExerciseInput;
import com.gymsync.api.program.dto.ProgramDetailResponse;
import com.gymsync.api.program.dto.ProgramSummaryResponse;
import com.gymsync.api.program.dto.StartProgramDayResponse;
import com.gymsync.api.program.dto.UpdateProgramDayRequest;
import com.gymsync.api.program.dto.UpdateProgramRequest;
import com.gymsync.api.program.repository.ProgramDayRepository;
import com.gymsync.api.program.repository.ProgramRepository;
import com.gymsync.api.program.repository.ProgramSpecifications;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import com.gymsync.api.workout.dto.WorkoutResponse;
import com.gymsync.api.workout.service.WorkoutService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramService {

  private static final String NOT_FOUND_MESSAGE = "Program not found.";
  private static final String DAY_NOT_FOUND_MESSAGE = "Program day not found.";
  private static final String DAY_CONFLICT_MESSAGE =
      "A day with this day number already exists in the program.";

  private final ProgramRepository programRepository;
  private final ProgramDayRepository programDayRepository;
  private final UserRepository userRepository;
  private final ExerciseRepository exerciseRepository;
  private final WorkoutService workoutService;

  public ProgramService(
      ProgramRepository programRepository,
      ProgramDayRepository programDayRepository,
      UserRepository userRepository,
      ExerciseRepository exerciseRepository,
      WorkoutService workoutService) {
    this.programRepository = programRepository;
    this.programDayRepository = programDayRepository;
    this.userRepository = userRepository;
    this.exerciseRepository = exerciseRepository;
    this.workoutService = workoutService;
  }

  @Transactional(readOnly = true)
  public List<ProgramSummaryResponse> listPrograms(UUID userId) {
    User user = userRepository.getReferenceById(userId);
    Specification<Program> spec = Specification.allOf(ProgramSpecifications.forUser(user));
    return programRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
        .map(ProgramSummaryResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public ProgramDetailResponse getProgram(UUID userId, UUID programId) {
    return ProgramDetailResponse.from(requireOwned(userId, programId));
  }

  @Transactional
  public ProgramSummaryResponse createProgram(UUID userId, CreateProgramRequest request) {
    User user = userRepository.getReferenceById(userId);
    Program program = new Program(user, request.name().trim(), blankToNull(request.description()));
    programRepository.save(program);
    return ProgramSummaryResponse.from(program);
  }

  @Transactional
  public ProgramSummaryResponse updateProgram(
      UUID userId, UUID programId, UpdateProgramRequest request) {
    Program program = requireOwned(userId, programId);
    program.rename(request.name().trim(), blankToNull(request.description()));
    return ProgramSummaryResponse.from(program);
  }

  @Transactional
  public void deleteProgram(UUID userId, UUID programId) {
    Program program = requireOwned(userId, programId);
    programRepository.delete(program);
  }

  @Transactional
  public ProgramDetailResponse.ProgramDayResponse addDay(
      UUID userId, UUID programId, CreateProgramDayRequest request) {
    Program program = requireOwned(userId, programId);
    if (programDayRepository.existsByProgramIdAndDayNumber(programId, request.dayNumber())) {
      throw new ConflictException(DAY_CONFLICT_MESSAGE);
    }
    ProgramDay day = new ProgramDay(program, request.dayNumber(), request.name().trim());
    replaceExercises(userId, day, request.exercises());
    program.addDay(day);
    try {
      programDayRepository.saveAndFlush(day);
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException(DAY_CONFLICT_MESSAGE);
    }
    return ProgramDetailResponse.ProgramDayResponse.from(day);
  }

  @Transactional
  public ProgramDetailResponse.ProgramDayResponse updateDay(
      UUID userId, UUID programId, int dayNumber, UpdateProgramDayRequest request) {
    requireOwned(userId, programId);
    ProgramDay day =
        programDayRepository
            .findByProgramIdAndDayNumber(programId, dayNumber)
            .orElseThrow(() -> new NotFoundException(DAY_NOT_FOUND_MESSAGE));
    day.rename(request.name().trim());
    day.clearExercises();
    // Flush deletes before inserts so unique (program_day_id, order_in_day) does not collide.
    programDayRepository.saveAndFlush(day);
    replaceExercises(userId, day, request.exercises());
    return ProgramDetailResponse.ProgramDayResponse.from(day);
  }

  @Transactional
  public void deleteDay(UUID userId, UUID programId, int dayNumber) {
    requireOwned(userId, programId);
    ProgramDay day =
        programDayRepository
            .findByProgramIdAndDayNumber(programId, dayNumber)
            .orElseThrow(() -> new NotFoundException(DAY_NOT_FOUND_MESSAGE));
    Program program = day.getProgram();
    program.removeDay(day);
    programDayRepository.delete(day);
  }

  @Transactional
  public StartProgramDayResponse startDay(UUID userId, UUID programId, int dayNumber) {
    requireOwned(userId, programId);
    ProgramDay day =
        programDayRepository
            .findByProgramIdAndDayNumber(programId, dayNumber)
            .orElseThrow(() -> new NotFoundException(DAY_NOT_FOUND_MESSAGE));
    // Reuses WorkoutService start path (pre-check + unique index → 409 if already ACTIVE).
    WorkoutResponse workout = workoutService.startWorkout(userId);
    List<ProgramDetailResponse.PlannedExerciseResponse> planned =
        day.getExercises().stream()
            .map(ProgramDetailResponse.PlannedExerciseResponse::from)
            .toList();
    return new StartProgramDayResponse(workout, planned);
  }

  private void replaceExercises(UUID userId, ProgramDay day, List<ProgramDayExerciseInput> inputs) {
    Set<Integer> orders = new HashSet<>();
    for (ProgramDayExerciseInput input : inputs) {
      if (!orders.add(input.orderInDay())) {
        throw new BadRequestException("orderInDay values must be unique within a day.");
      }
      if (input.targetRepsMin() != null
          && input.targetRepsMax() != null
          && input.targetRepsMin() > input.targetRepsMax()) {
        throw new BadRequestException("targetRepsMin must be <= targetRepsMax.");
      }
      Exercise exercise = requireVisibleExercise(userId, input.exerciseId());
      day.addExercise(
          new ProgramDayExercise(
              day,
              exercise,
              input.orderInDay(),
              input.targetSets(),
              input.targetRepsMin(),
              input.targetRepsMax(),
              input.targetWeight()));
    }
  }

  private Exercise requireVisibleExercise(UUID userId, UUID exerciseId) {
    Exercise exercise =
        exerciseRepository
            .findById(exerciseId)
            .orElseThrow(() -> new NotFoundException("Exercise not found."));
    boolean visible = exercise.isGlobal() || exercise.getCreatedBy().getId().equals(userId);
    if (!visible) {
      throw new NotFoundException("Exercise not found.");
    }
    return exercise;
  }

  private Program requireOwned(UUID userId, UUID programId) {
    return programRepository
        .findByIdAndUserId(programId, userId)
        .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE));
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
