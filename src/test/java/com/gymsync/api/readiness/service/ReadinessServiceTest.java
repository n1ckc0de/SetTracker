package com.gymsync.api.readiness.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymsync.api.common.exception.NotFoundException;
import com.gymsync.api.readiness.domain.ReadinessCheckIn;
import com.gymsync.api.readiness.dto.CreateReadinessRequest;
import com.gymsync.api.readiness.dto.ReadinessResponse;
import com.gymsync.api.readiness.repository.ReadinessCheckInRepository;
import com.gymsync.api.user.domain.Role;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import com.gymsync.api.workout.repository.WorkoutRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReadinessServiceTest {

  @Mock private ReadinessCheckInRepository readinessCheckInRepository;
  @Mock private UserRepository userRepository;
  @Mock private WorkoutRepository workoutRepository;

  private ReadinessService service;
  private User user;
  private UUID userId;

  @BeforeEach
  void setUp() {
    service = new ReadinessService(readinessCheckInRepository, userRepository, workoutRepository);
    userId = UUID.randomUUID();
    user = new User("ready@example.com", "hash", "Ready", Role.USER);
  }

  @Test
  void create_persistsPartialCheckIn() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(readinessCheckInRepository.save(any()))
        .thenAnswer(
            inv -> {
              ReadinessCheckIn c = inv.getArgument(0);
              return c;
            });

    ReadinessResponse response =
        service.create(
            userId,
            new CreateReadinessRequest(null, new BigDecimal("7.5"), 4, 3, 2, 1, "  slept ok  "));

    ArgumentCaptor<ReadinessCheckIn> captor = ArgumentCaptor.forClass(ReadinessCheckIn.class);
    verify(readinessCheckInRepository).save(captor.capture());
    assertThat(captor.getValue().getSleepQuality()).isEqualTo(4);
    assertThat(captor.getValue().getNote()).isEqualTo("slept ok");
    assertThat(response.sleepHours()).isEqualByComparingTo("7.5");
  }

  @Test
  void create_withUnknownWorkout_throws() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    UUID workoutId = UUID.randomUUID();
    when(workoutRepository.findByIdAndUserId(workoutId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.create(
                    userId,
                    new CreateReadinessRequest(workoutId, null, null, null, null, null, null)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void latest_returnsMostRecent() {
    when(userRepository.getReferenceById(userId)).thenReturn(user);
    ReadinessCheckIn checkIn = new ReadinessCheckIn(user);
    checkIn.setEnergyLevel(5);
    when(readinessCheckInRepository.findFirstByUserOrderByCreatedAtDesc(user))
        .thenReturn(Optional.of(checkIn));

    assertThat(service.latest(userId).energyLevel()).isEqualTo(5);
  }

  @Test
  void latest_whenEmpty_throws() {
    when(userRepository.getReferenceById(userId)).thenReturn(user);
    when(readinessCheckInRepository.findFirstByUserOrderByCreatedAtDesc(user))
        .thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.latest(userId)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void list_mapsRange() {
    when(userRepository.getReferenceById(userId)).thenReturn(user);
    ReadinessCheckIn checkIn = new ReadinessCheckIn(user);
    checkIn.setSoreness(3);
    when(readinessCheckInRepository.findByUserAndCreatedAtBetween(
            any(), any(Instant.class), any(Instant.class)))
        .thenReturn(List.of(checkIn));

    List<ReadinessResponse> list =
        service.list(userId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    assertThat(list).hasSize(1);
    assertThat(list.getFirst().soreness()).isEqualTo(3);
  }
}
