package com.gymsync.api.workout.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymsync.api.AbstractIntegrationTest;
import com.gymsync.api.exercise.domain.ExerciseCategory;
import com.gymsync.api.exercise.dto.CreateExerciseRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class WorkoutSetControllerTest extends AbstractIntegrationTest {

  private String startWorkout(RegisteredUser user) throws Exception {
    String response =
        mockMvc
            .perform(post("/api/workouts").header("Authorization", user.authorizationHeader()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response).get("id").asText();
  }

  private String globalExerciseId(RegisteredUser user, String query) throws Exception {
    String response =
        mockMvc
            .perform(
                get("/api/exercises")
                    .header("Authorization", user.authorizationHeader())
                    .param("query", query))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response).get("content").get(0).get("id").asText();
  }

  private String createSetRequestBody(String exerciseId, String weight, int reps, int setNumber) {
    return """
    {"exerciseId":"%s","weight":%s,"reps":%d,"setNumber":%d}
    """
        .formatted(exerciseId, weight, reps, setNumber);
  }

  @Test
  void addSet_valid_returns201WithExerciseInfo() throws Exception {
    RegisteredUser user = registerUser("setuser1@example.com", "supersecret1", "Set User One");
    String workoutId = startWorkout(user);
    String exerciseId = globalExerciseId(user, "bench press");

    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSetRequestBody(exerciseId, "135.5", 8, 1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.exerciseName").value("Bench Press"))
        .andExpect(jsonPath("$.weight").value(135.5))
        .andExpect(jsonPath("$.reps").value(8))
        .andExpect(jsonPath("$.setNumber").value(1))
        .andExpect(jsonPath("$.weightEntryMode").value("TOTAL"))
        .andExpect(jsonPath("$.enteredWeight").value(135.5))
        .andExpect(jsonPath("$.completed").value(true));
  }

  @Test
  void addSet_missingWorkout_returns404() throws Exception {
    RegisteredUser user = registerUser("setuser2@example.com", "supersecret1", "Set User Two");
    String exerciseId = globalExerciseId(user, "squat");
    String missingWorkoutId = UUID.randomUUID().toString();

    mockMvc
        .perform(
            post("/api/workouts/" + missingWorkoutId + "/sets")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSetRequestBody(exerciseId, "100", 5, 1)))
        .andExpect(status().isNotFound());
  }

  @Test
  void addSet_onCompletedWorkout_returns409() throws Exception {
    RegisteredUser user = registerUser("setuser3@example.com", "supersecret1", "Set User Three");
    String workoutId = startWorkout(user);
    mockMvc
        .perform(
            patch("/api/workouts/" + workoutId + "/complete")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk());
    String exerciseId = globalExerciseId(user, "squat");

    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSetRequestBody(exerciseId, "100", 5, 1)))
        .andExpect(status().isConflict());
  }

  @Test
  void addSet_onAnotherUsersWorkout_returns404() throws Exception {
    RegisteredUser owner = registerUser("setowner@example.com", "supersecret1", "Set Owner");
    RegisteredUser other = registerUser("setother@example.com", "supersecret1", "Set Other");
    String workoutId = startWorkout(owner);
    String exerciseId = globalExerciseId(other, "deadlift");

    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", other.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSetRequestBody(exerciseId, "100", 5, 1)))
        .andExpect(status().isNotFound());
  }

  @Test
  void addSet_withInvalidExercise_returns404() throws Exception {
    RegisteredUser user = registerUser("setuser4@example.com", "supersecret1", "Set User Four");
    String workoutId = startWorkout(user);
    String bogusExerciseId = UUID.randomUUID().toString();

    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSetRequestBody(bogusExerciseId, "100", 5, 1)))
        .andExpect(status().isNotFound());
  }

  @Test
  void addSet_withAnotherUsersPrivateExercise_returns404() throws Exception {
    RegisteredUser exerciseOwner =
        registerUser("setexowner@example.com", "supersecret1", "Ex Owner");
    RegisteredUser requester =
        registerUser("setexrequester@example.com", "supersecret1", "Requester");

    String createExerciseResponse =
        mockMvc
            .perform(
                post("/api/exercises")
                    .header("Authorization", exerciseOwner.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreateExerciseRequest(
                                "Owner's Secret Move", ExerciseCategory.OTHER, null, null, null))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String privateExerciseId = objectMapper.readTree(createExerciseResponse).get("id").asText();

    String workoutId = startWorkout(requester);
    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", requester.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSetRequestBody(privateExerciseId, "50", 10, 1)))
        .andExpect(status().isNotFound());
  }

  @Test
  void addSet_withNegativeWeight_returns400() throws Exception {
    RegisteredUser user = registerUser("setuser5@example.com", "supersecret1", "Set User Five");
    String workoutId = startWorkout(user);
    String exerciseId = globalExerciseId(user, "squat");

    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSetRequestBody(exerciseId, "-10", 5, 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors.weight", org.hamcrest.Matchers.notNullValue()));
  }

  @Test
  void addSet_withZeroReps_returns400() throws Exception {
    RegisteredUser user = registerUser("setuser6@example.com", "supersecret1", "Set User Six");
    String workoutId = startWorkout(user);
    String exerciseId = globalExerciseId(user, "squat");

    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSetRequestBody(exerciseId, "100", 0, 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors.reps", org.hamcrest.Matchers.notNullValue()));
  }

  @Test
  void addSet_withDuplicateSetNumberForSameExercise_returns409() throws Exception {
    RegisteredUser user = registerUser("setuser7@example.com", "supersecret1", "Set User Seven");
    String workoutId = startWorkout(user);
    String exerciseId = globalExerciseId(user, "squat");

    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSetRequestBody(exerciseId, "100", 5, 1)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSetRequestBody(exerciseId, "105", 4, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void updateSet_changesWeightRepsAndSetNumber() throws Exception {
    RegisteredUser user = registerUser("setuser8@example.com", "supersecret1", "Set User Eight");
    String workoutId = startWorkout(user);
    String exerciseId = globalExerciseId(user, "squat");

    String createResponse =
        mockMvc
            .perform(
                post("/api/workouts/" + workoutId + "/sets")
                    .header("Authorization", user.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createSetRequestBody(exerciseId, "100", 5, 1)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String setId = objectMapper.readTree(createResponse).get("id").asText();

    mockMvc
        .perform(
            put("/api/sets/" + setId)
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enteredWeight":110,"weightEntryMode":"TOTAL","reps":6,"setNumber":2}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weight").value(110))
        .andExpect(jsonPath("$.reps").value(6))
        .andExpect(jsonPath("$.setNumber").value(2))
        .andExpect(jsonPath("$.enteredWeight").value(110))
        .andExpect(jsonPath("$.weightEntryMode").value("TOTAL"));
  }

  @Test
  void deleteSet_removesIt() throws Exception {
    RegisteredUser user = registerUser("setuser9@example.com", "supersecret1", "Set User Nine");
    String workoutId = startWorkout(user);
    String exerciseId = globalExerciseId(user, "squat");

    String createResponse =
        mockMvc
            .perform(
                post("/api/workouts/" + workoutId + "/sets")
                    .header("Authorization", user.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createSetRequestBody(exerciseId, "100", 5, 1)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String setId = objectMapper.readTree(createResponse).get("id").asText();

    mockMvc
        .perform(delete("/api/sets/" + setId).header("Authorization", user.authorizationHeader()))
        .andExpect(status().isNoContent());

    String listResponse =
        mockMvc
            .perform(
                get("/api/workouts/" + workoutId + "/sets")
                    .header("Authorization", user.authorizationHeader()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode sets = objectMapper.readTree(listResponse);
    assertThat(sets).isEmpty();
  }

  @Test
  void fullWorkoutFlow_startAddSetsCompleteAndRetrieveDetail() throws Exception {
    RegisteredUser user = registerUser("setuser10@example.com", "supersecret1", "Set User Ten");
    String workoutId = startWorkout(user);
    String benchId = globalExerciseId(user, "bench press");
    String squatId = globalExerciseId(user, "squat");

    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSetRequestBody(benchId, "135", 8, 1)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSetRequestBody(squatId, "185", 5, 1)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            patch("/api/workouts/" + workoutId + "/complete")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.sets.length()").value(2));

    mockMvc
        .perform(
            get("/api/workouts/" + workoutId).header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sets.length()").value(2));

    // Completed workouts reject new sets.
    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSetRequestBody(benchId, "140", 6, 2)))
        .andExpect(status().isConflict());
  }

  @Test
  void addSet_perSide_normalizesTotalWeight() throws Exception {
    RegisteredUser user = registerUser("set-per-side@example.com", "supersecret1", "Per Side");
    String workoutId = startWorkout(user);
    String exerciseId = globalExerciseId(user, "bench press");

    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"exerciseId":"%s","enteredWeight":50,"weightEntryMode":"PER_SIDE","barWeight":45,"reps":8,"setNumber":1}
                    """
                        .formatted(exerciseId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.enteredWeight").value(50))
        .andExpect(jsonPath("$.barWeight").value(45))
        .andExpect(jsonPath("$.weightEntryMode").value("PER_SIDE"))
        .andExpect(jsonPath("$.weight").value(145));
  }

  @Test
  void addBatchSets_createsMultipleSets() throws Exception {
    RegisteredUser user = registerUser("set-batch@example.com", "supersecret1", "Batch");
    String workoutId = startWorkout(user);
    String exerciseId = globalExerciseId(user, "squat");

    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets/batch")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"exerciseId":"%s","count":3,"enteredWeight":100,"weightEntryMode":"TOTAL","reps":5,"startingSetNumber":1}
                    """
                        .formatted(exerciseId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].setNumber").value(1))
        .andExpect(jsonPath("$[2].setNumber").value(3));
  }

  @Test
  void completeSet_marksIncompleteSetCompleted() throws Exception {
    RegisteredUser user = registerUser("set-complete@example.com", "supersecret1", "Complete");
    String workoutId = startWorkout(user);
    String exerciseId = globalExerciseId(user, "deadlift");

    String createResponse =
        mockMvc
            .perform(
                post("/api/workouts/" + workoutId + "/sets")
                    .header("Authorization", user.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"exerciseId":"%s","enteredWeight":225,"weightEntryMode":"TOTAL","reps":5,"setNumber":1,"completed":false}
                        """
                            .formatted(exerciseId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.completed").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String setId = objectMapper.readTree(createResponse).get("id").asText();

    mockMvc
        .perform(
            patch("/api/sets/" + setId + "/complete")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed").value(true));
  }
}
