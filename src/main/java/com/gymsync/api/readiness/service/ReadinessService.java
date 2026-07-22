package com.gymsync.api.readiness.service;

import com.gymsync.api.common.exception.NotFoundException;
import com.gymsync.api.readiness.domain.ReadinessCheckIn;
import com.gymsync.api.readiness.dto.CreateReadinessRequest;
import com.gymsync.api.readiness.dto.ReadinessResponse;
import com.gymsync.api.readiness.repository.ReadinessCheckInRepository;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import com.gymsync.api.workout.repository.WorkoutRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadinessService {

  private final ReadinessCheckInRepository readinessCheckInRepository;
  private final UserRepository userRepository;
  private final WorkoutRepository workoutRepository;

  public ReadinessService(
      ReadinessCheckInRepository readinessCheckInRepository,
      UserRepository userRepository,
      WorkoutRepository workoutRepository) {
    this.readinessCheckInRepository = readinessCheckInRepository;
    this.userRepository = userRepository;
    this.workoutRepository = workoutRepository;
  }

  @Transactional
  public ReadinessResponse create(UUID userId, CreateReadinessRequest request) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found."));
    if (request.workoutId() != null
        && workoutRepository.findByIdAndUserId(request.workoutId(), userId).isEmpty()) {
      throw new NotFoundException("Workout not found.");
    }

    ReadinessCheckIn checkIn = new ReadinessCheckIn(user);
    checkIn.setWorkoutId(request.workoutId());
    checkIn.setSleepHours(request.sleepHours());
    checkIn.setSleepQuality(request.sleepQuality());
    checkIn.setEnergyLevel(request.energyLevel());
    checkIn.setSoreness(request.soreness());
    checkIn.setStress(request.stress());
    checkIn.setNote(
        request.note() == null || request.note().isBlank() ? null : request.note().trim());
    return ReadinessResponse.from(readinessCheckInRepository.save(checkIn));
  }

  @Transactional(readOnly = true)
  public ReadinessResponse latest(UUID userId) {
    User user = userRepository.getReferenceById(userId);
    return readinessCheckInRepository
        .findFirstByUserOrderByCreatedAtDesc(user)
        .map(ReadinessResponse::from)
        .orElseThrow(() -> new NotFoundException("No readiness check-in found."));
  }

  @Transactional(readOnly = true)
  public List<ReadinessResponse> list(UUID userId, LocalDate from, LocalDate to) {
    User user = userRepository.getReferenceById(userId);
    Instant fromInstant =
        (from == null ? LocalDate.now(ZoneOffset.UTC).minusDays(30) : from)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();
    Instant toInstant =
        (to == null ? LocalDate.now(ZoneOffset.UTC).plusDays(1) : to.plusDays(1))
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();
    if (to != null) {
      toInstant = to.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC).toInstant().plusMillis(1);
    }
    return readinessCheckInRepository
        .findByUserAndCreatedAtBetween(user, fromInstant, toInstant)
        .stream()
        .map(ReadinessResponse::from)
        .toList();
  }
}
