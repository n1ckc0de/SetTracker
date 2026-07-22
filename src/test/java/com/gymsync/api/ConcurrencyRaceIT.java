package com.gymsync.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymsync.api.checkin.controller.CheckInWebhookController;
import com.gymsync.api.checkin.service.WebhookSignatureValidator;
import com.gymsync.api.common.exception.ConflictException;
import com.gymsync.api.notesimport.service.NotesImportService;
import com.gymsync.api.workout.service.WorkoutService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * True multi-threaded race coverage for the concurrency invariants documented in {@code
 * docs/CONCURRENCY.md}. These tests intentionally fire concurrent requests against a real
 * PostgreSQL (Testcontainers) so the database constraints — not in-memory locks — are what win.
 */
class ConcurrencyRaceIT extends AbstractIntegrationTest {

  @Autowired private WorkoutService workoutService;

  @Autowired private NotesImportService notesImportService;

  @Autowired private WebhookSignatureValidator signatureValidator;

  @Test
  void concurrentStartWorkout_onlyOneActiveWorkoutIsCreated() throws Exception {
    RegisteredUser user = registerUser("race-start@example.com", "supersecret1", "Race Start");
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();

    runConcurrently(
        2,
        () -> {
          try {
            workoutService.startWorkout(user.id());
            successes.incrementAndGet();
          } catch (ConflictException ex) {
            conflicts.incrementAndGet();
          }
          return null;
        });

    assertThat(successes.get()).isEqualTo(1);
    assertThat(conflicts.get()).isEqualTo(1);

    String list =
        mockMvc
            .perform(
                get("/api/workouts")
                    .header("Authorization", user.authorizationHeader())
                    .param("status", "ACTIVE"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(objectMapper.readTree(list).get("content")).hasSize(1);
  }

  @Test
  void concurrentDuplicateWebhook_createsOnlyOneWorkout() throws Exception {
    RegisteredUser user = registerUser("race-webhook@example.com", "supersecret1", "Race Webhook");
    String body =
        """
        {"eventId":"evt-race-dup","userId":"%s","source":"gym-turnstile-mock"}
        """
            .formatted(user.id())
            .strip();
    String timestamp = String.valueOf(Instant.now().getEpochSecond());
    String signature = signatureValidator.sign(timestamp, body.getBytes(StandardCharsets.UTF_8));

    AtomicInteger created = new AtomicInteger();
    AtomicInteger duplicates = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();

    runConcurrently(
        2,
        () -> {
          MvcResult result =
              mockMvc
                  .perform(
                      post("/api/webhooks/checkin")
                          .contentType(MediaType.APPLICATION_JSON)
                          .header(CheckInWebhookController.TIMESTAMP_HEADER, timestamp)
                          .header(CheckInWebhookController.SIGNATURE_HEADER, signature)
                          .content(body))
                  .andReturn();
          int status = result.getResponse().getStatus();
          if (status == 201) {
            created.incrementAndGet();
          } else if (status == 200) {
            duplicates.incrementAndGet();
          } else if (status == 409) {
            // Loser may lose the active-workout race before the winner persists the event row.
            conflicts.incrementAndGet();
          } else {
            throw new IllegalStateException(
                "Unexpected status " + status + ": " + result.getResponse().getContentAsString());
          }
          return null;
        });

    assertThat(created.get()).isEqualTo(1);
    assertThat(duplicates.get() + conflicts.get()).isEqualTo(1);

    String list =
        mockMvc
            .perform(
                get("/api/workouts")
                    .header("Authorization", user.authorizationHeader())
                    .param("source", "WEBHOOK"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(objectMapper.readTree(list).get("content")).hasSize(1);
  }

  @Test
  void concurrentCompleteWorkout_onlyOneSucceeds() throws Exception {
    RegisteredUser user =
        registerUser("race-complete@example.com", "supersecret1", "Race Complete");
    String createBody =
        mockMvc
            .perform(post("/api/workouts").header("Authorization", user.authorizationHeader()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID workoutId = UUID.fromString(objectMapper.readTree(createBody).get("id").asText());

    AtomicInteger successes = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();

    runConcurrently(
        2,
        () -> {
          try {
            workoutService.completeWorkout(user.id(), workoutId);
            successes.incrementAndGet();
          } catch (ConflictException ex) {
            conflicts.incrementAndGet();
          }
          return null;
        });

    assertThat(successes.get()).isEqualTo(1);
    assertThat(conflicts.get()).isEqualTo(1);

    mockMvc
        .perform(
            get("/api/workouts/" + workoutId).header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                assertThat(
                        objectMapper
                            .readTree(result.getResponse().getContentAsString())
                            .get("status")
                            .asText())
                    .isEqualTo("COMPLETED"));
  }

  @Test
  void concurrentConfirmImport_onlyOneSucceeds() throws Exception {
    RegisteredUser user = registerUser("race-import@example.com", "supersecret1", "Race Import");
    String importBody =
        objectMapper.writeValueAsString(java.util.Map.of("rawText", "Bench 3x8 135 lb"));
    String importResponse =
        mockMvc
            .perform(
                post("/api/notes/import")
                    .header("Authorization", user.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(importBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID workoutId = UUID.fromString(objectMapper.readTree(importResponse).get("id").asText());

    AtomicInteger successes = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();

    runConcurrently(
        2,
        () -> {
          try {
            notesImportService.confirmImport(user.id(), workoutId);
            successes.incrementAndGet();
          } catch (ConflictException ex) {
            conflicts.incrementAndGet();
          }
          return null;
        });

    assertThat(successes.get()).isEqualTo(1);
    assertThat(conflicts.get()).isEqualTo(1);

    mockMvc
        .perform(
            get("/api/workouts/" + workoutId).header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                assertThat(
                        objectMapper
                            .readTree(result.getResponse().getContentAsString())
                            .get("importStatus")
                            .asText())
                    .isEqualTo("CONFIRMED"));

    // Confirming again through HTTP must also be rejected.
    mockMvc
        .perform(
            patch("/api/workouts/" + workoutId + "/confirm-import")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isConflict());
  }

  private static void runConcurrently(int threads, Callable<Void> task) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Void>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < threads; i++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start");
                  }
                  return task.call();
                }));
      }
      if (!ready.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Workers did not become ready");
      }
      start.countDown();
      for (Future<Void> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }
  }
}
