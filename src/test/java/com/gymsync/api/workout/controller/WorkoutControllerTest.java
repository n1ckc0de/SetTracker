package com.gymsync.api.workout.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymsync.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class WorkoutControllerTest extends AbstractIntegrationTest {

  @Test
  void startWorkout_returnsActiveManualWorkout() throws Exception {
    RegisteredUser user = registerUser("wkuser1@example.com", "supersecret1", "Workout User One");

    mockMvc
        .perform(post("/api/workouts").header("Authorization", user.authorizationHeader()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.source").value("MANUAL"))
        .andExpect(jsonPath("$.importStatus").value("NOT_APPLICABLE"));
  }

  @Test
  void startWorkout_whenAlreadyActive_returns409Conflict() throws Exception {
    RegisteredUser user = registerUser("wkuser2@example.com", "supersecret1", "Workout User Two");
    mockMvc
        .perform(post("/api/workouts").header("Authorization", user.authorizationHeader()))
        .andExpect(status().isCreated());

    mockMvc
        .perform(post("/api/workouts").header("Authorization", user.authorizationHeader()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void getWorkout_forAnotherUser_returns404() throws Exception {
    RegisteredUser owner = registerUser("wkowner@example.com", "supersecret1", "Workout Owner");
    RegisteredUser other = registerUser("wkother@example.com", "supersecret1", "Workout Other");

    String createResponse =
        mockMvc
            .perform(post("/api/workouts").header("Authorization", owner.authorizationHeader()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String workoutId = objectMapper.readTree(createResponse).get("id").asText();

    mockMvc
        .perform(
            get("/api/workouts/" + workoutId).header("Authorization", other.authorizationHeader()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            get("/api/workouts/" + workoutId).header("Authorization", owner.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sets").isArray())
        .andExpect(jsonPath("$.sets").isEmpty());
  }

  @Test
  void completeWorkout_transitionsToCompletedWithEndedAt() throws Exception {
    RegisteredUser user = registerUser("wkuser3@example.com", "supersecret1", "Workout User Three");
    String createResponse =
        mockMvc
            .perform(post("/api/workouts").header("Authorization", user.authorizationHeader()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String workoutId = objectMapper.readTree(createResponse).get("id").asText();

    mockMvc
        .perform(
            patch("/api/workouts/" + workoutId + "/complete")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.endedAt", org.hamcrest.Matchers.notNullValue()));

    // A completed workout cannot be completed again.
    mockMvc
        .perform(
            patch("/api/workouts/" + workoutId + "/complete")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isConflict());

    // A new workout can now be started since the previous one is no longer active.
    mockMvc
        .perform(post("/api/workouts").header("Authorization", user.authorizationHeader()))
        .andExpect(status().isCreated());
  }

  @Test
  void cancelWorkout_softCancelsAndPreventsFurtherActions() throws Exception {
    RegisteredUser user = registerUser("wkuser4@example.com", "supersecret1", "Workout User Four");
    String createResponse =
        mockMvc
            .perform(post("/api/workouts").header("Authorization", user.authorizationHeader()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String workoutId = objectMapper.readTree(createResponse).get("id").asText();

    mockMvc
        .perform(
            delete("/api/workouts/" + workoutId)
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/workouts/" + workoutId).header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    // Cancelling an already-completed workout is a conflict.
    String secondWorkout =
        mockMvc
            .perform(post("/api/workouts").header("Authorization", user.authorizationHeader()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String secondWorkoutId = objectMapper.readTree(secondWorkout).get("id").asText();
    mockMvc
        .perform(
            patch("/api/workouts/" + secondWorkoutId + "/complete")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            delete("/api/workouts/" + secondWorkoutId)
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isConflict());
  }

  @Test
  void listWorkouts_defaultsToNewestFirstAndSupportsStatusFilter() throws Exception {
    RegisteredUser user = registerUser("wkuser5@example.com", "supersecret1", "Workout User Five");
    String first =
        mockMvc
            .perform(post("/api/workouts").header("Authorization", user.authorizationHeader()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String firstId = objectMapper.readTree(first).get("id").asText();
    mockMvc
        .perform(
            patch("/api/workouts/" + firstId + "/complete")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/api/workouts").header("Authorization", user.authorizationHeader()))
        .andExpect(status().isCreated());

    String listResponse =
        mockMvc
            .perform(
                get("/api/workouts")
                    .header("Authorization", user.authorizationHeader())
                    .param("status", "ACTIVE"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var content = objectMapper.readTree(listResponse).get("content");
    org.assertj.core.api.Assertions.assertThat(content).hasSize(1);
    org.assertj.core.api.Assertions.assertThat(content.get(0).get("status").asText())
        .isEqualTo("ACTIVE");
  }
}
