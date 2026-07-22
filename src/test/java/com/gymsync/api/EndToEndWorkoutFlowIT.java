package com.gymsync.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Full end-to-end happy path exercised through the real HTTP layer, per spec section 22: register
 * -> login -> select an exercise -> start a workout -> log sets -> complete it -> retrieve history
 * -> retrieve weekly stats -> retrieve a progressive-overload recommendation.
 *
 * <p>Named {@code *IT} so it runs under the failsafe plugin ({@code ./mvnw verify}) alongside the
 * narrower, faster surefire ({@code ./mvnw test}) suite.
 */
class EndToEndWorkoutFlowIT extends AbstractIntegrationTest {

  @Test
  void fullHappyPathFlow() throws Exception {
    String email = "e2e-user@example.com";
    String password = "supersecret1";

    // 1. Register.
    RegisteredUser registered = registerUser(email, password, "End To End User");

    // 2. Login (independently of the registration response) to confirm credentials work.
    String loginResponse =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email":"%s","password":"%s"}
                        """
                            .formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String accessToken = objectMapper.readTree(loginResponse).get("accessToken").asText();
    String authHeader = "Bearer " + accessToken;

    // 3. Select an existing (seeded) global exercise.
    String exerciseSearch =
        mockMvc
            .perform(
                get("/api/exercises")
                    .header("Authorization", authHeader)
                    .param("query", "bench press"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String exerciseId =
        objectMapper.readTree(exerciseSearch).get("content").get(0).get("id").asText();

    // 4. Start a workout.
    String startResponse =
        mockMvc
            .perform(post("/api/workouts").header("Authorization", authHeader))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String workoutId = objectMapper.readTree(startResponse).get("id").asText();

    // 5. Log sets (progressing to the top of the default target rep range).
    addSet(authHeader, workoutId, exerciseId, "135", 8, 1);
    addSet(authHeader, workoutId, exerciseId, "135", 10, 2);
    addSet(authHeader, workoutId, exerciseId, "135", 12, 3);

    // 6. Complete the workout.
    mockMvc
        .perform(
            patch("/api/workouts/" + workoutId + "/complete").header("Authorization", authHeader))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.sets.length()").value(3));

    // 7. Retrieve workout history.
    String historyResponse =
        mockMvc
            .perform(
                get("/api/workouts")
                    .header("Authorization", authHeader)
                    .param("status", "COMPLETED"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode historyContent = objectMapper.readTree(historyResponse).get("content");
    assertThat(historyContent).hasSize(1);

    // 8. Retrieve weekly statistics; this workout is MANUAL (not an import), so it counts
    // immediately.
    mockMvc
        .perform(get("/api/stats/weekly").header("Authorization", authHeader))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completedWorkouts").value(1))
        .andExpect(jsonPath("$.totalSets").value(3))
        .andExpect(jsonPath("$.totalReps").value(30))
        .andExpect(jsonPath("$.volumeByExercise[0].exerciseName").value("Bench Press"));

    // 9. Retrieve a personal record.
    mockMvc
        .perform(get("/api/exercises/" + exerciseId + "/pr").header("Authorization", authHeader))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.heaviestWeight").value(135))
        .andExpect(jsonPath("$.repsAtHeaviestWeight").value(12));

    // 10. Retrieve a progressive-overload recommendation for the next session.
    mockMvc
        .perform(
            get("/api/exercises/" + exerciseId + "/next-session-recommendation")
                .header("Authorization", authHeader))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.basedOnWorkoutId").value(workoutId))
        .andExpect(jsonPath("$.previousPerformance.weight").value(135))
        .andExpect(jsonPath("$.previousPerformance.reps").value(12))
        .andExpect(jsonPath("$.recommendation.optionA").exists())
        .andExpect(jsonPath("$.disclaimer", org.hamcrest.Matchers.containsString("not medical")));
  }

  private void addSet(
      String authHeader,
      String workoutId,
      String exerciseId,
      String weight,
      int reps,
      int setNumber)
      throws Exception {
    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"exerciseId":"%s","weight":%s,"reps":%d,"setNumber":%d}
                    """
                        .formatted(exerciseId, weight, reps, setNumber)))
        .andExpect(status().isCreated());
  }
}
