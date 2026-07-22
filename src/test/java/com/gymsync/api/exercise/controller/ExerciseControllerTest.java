package com.gymsync.api.exercise.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymsync.api.AbstractIntegrationTest;
import com.gymsync.api.exercise.domain.ExerciseCategory;
import com.gymsync.api.exercise.dto.CreateExerciseRequest;
import com.gymsync.api.exercise.dto.UpdateExerciseRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ExerciseControllerTest extends AbstractIntegrationTest {

  @Test
  void getExerciseDetail_withoutPrHistory_returnsOkWithNullPersonalRecord() throws Exception {
    RegisteredUser user = registerUser("exdetail@example.com", "supersecret1", "Detail User");
    String listResponse =
        mockMvc
            .perform(
                get("/api/exercises")
                    .header("Authorization", user.authorizationHeader())
                    .param("query", "Bench Press")
                    .param("size", "5"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String exerciseId = null;
    for (JsonNode node : objectMapper.readTree(listResponse).get("content")) {
      if ("Bench Press".equals(node.get("name").asText())) {
        exerciseId = node.get("id").asText();
        break;
      }
    }
    org.assertj.core.api.Assertions.assertThat(exerciseId).isNotBlank();

    mockMvc
        .perform(
            get("/api/exercises/" + exerciseId + "/detail")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exercise.name").value("Bench Press"))
        .andExpect(jsonPath("$.personalRecord").doesNotExist())
        .andExpect(jsonPath("$.prHistory.points").isArray())
        .andExpect(jsonPath("$.recentSets").isArray());
  }

  @Test
  void createExercise_thenGetById_returnsOwnedExercise() throws Exception {
    RegisteredUser user = registerUser("exuser1@example.com", "supersecret1", "Ex User One");
    CreateExerciseRequest request =
        new CreateExerciseRequest("Cable Fly", ExerciseCategory.CHEST, null, null, null);

    String createResponse =
        mockMvc
            .perform(
                post("/api/exercises")
                    .header("Authorization", user.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Cable Fly"))
            .andExpect(jsonPath("$.global").value(false))
            .andExpect(jsonPath("$.owned").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String exerciseId = objectMapper.readTree(createResponse).get("id").asText();

    mockMvc
        .perform(
            get("/api/exercises/" + exerciseId).header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Cable Fly"));
  }

  @Test
  void listExercises_includesGlobalSeedExercisesAndOwnCustomOnes() throws Exception {
    RegisteredUser user = registerUser("exuser2@example.com", "supersecret1", "Ex User Two");
    mockMvc
        .perform(
            post("/api/exercises")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateExerciseRequest(
                            "My Custom Move", ExerciseCategory.OTHER, null, null, null))))
        .andExpect(status().isCreated());

    String listResponse =
        mockMvc
            .perform(
                get("/api/exercises")
                    .header("Authorization", user.authorizationHeader())
                    .param("size", "50"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode content = objectMapper.readTree(listResponse).get("content");
    boolean hasGlobalSeed = false;
    boolean hasCustom = false;
    for (JsonNode node : content) {
      if (node.get("name").asText().equals("Bench Press") && node.get("global").asBoolean()) {
        hasGlobalSeed = true;
      }
      if (node.get("name").asText().equals("My Custom Move") && node.get("owned").asBoolean()) {
        hasCustom = true;
      }
    }
    org.assertj.core.api.Assertions.assertThat(hasGlobalSeed).isTrue();
    org.assertj.core.api.Assertions.assertThat(hasCustom).isTrue();
  }

  @Test
  void listExercises_filtersByCategoryAndQuery() throws Exception {
    RegisteredUser user = registerUser("exuser3@example.com", "supersecret1", "Ex User Three");

    String listResponse =
        mockMvc
            .perform(
                get("/api/exercises")
                    .header("Authorization", user.authorizationHeader())
                    .param("query", "bench")
                    .param("category", "CHEST"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode content = objectMapper.readTree(listResponse).get("content");
    org.assertj.core.api.Assertions.assertThat(content).isNotEmpty();
    for (JsonNode node : content) {
      org.assertj.core.api.Assertions.assertThat(node.get("category").asText()).isEqualTo("CHEST");
    }
  }

  @Test
  void updateAndDeleteExercise_onlyWorksForOwner() throws Exception {
    RegisteredUser owner = registerUser("exowner@example.com", "supersecret1", "Owner");
    RegisteredUser other = registerUser("exother@example.com", "supersecret1", "Other");

    String createResponse =
        mockMvc
            .perform(
                post("/api/exercises")
                    .header("Authorization", owner.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreateExerciseRequest(
                                "Owner Move", ExerciseCategory.BACK, null, null, null))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String exerciseId = objectMapper.readTree(createResponse).get("id").asText();

    // Another user cannot see it as "theirs" to update.
    mockMvc
        .perform(
            put("/api/exercises/" + exerciseId)
                .header("Authorization", other.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new UpdateExerciseRequest(
                            "Hijacked", ExerciseCategory.BACK, null, null, null))))
        .andExpect(status().isNotFound());

    // Another user cannot delete it either.
    mockMvc
        .perform(
            delete("/api/exercises/" + exerciseId)
                .header("Authorization", other.authorizationHeader()))
        .andExpect(status().isNotFound());

    // Owner can update it.
    mockMvc
        .perform(
            put("/api/exercises/" + exerciseId)
                .header("Authorization", owner.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new UpdateExerciseRequest(
                            "Owner Move Renamed", ExerciseCategory.SHOULDERS, null, null, null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Owner Move Renamed"))
        .andExpect(jsonPath("$.category").value("SHOULDERS"));

    // Owner can delete it.
    mockMvc
        .perform(
            delete("/api/exercises/" + exerciseId)
                .header("Authorization", owner.authorizationHeader()))
        .andExpect(status().isNoContent());

    // Now truly gone for the owner too.
    mockMvc
        .perform(
            get("/api/exercises/" + exerciseId)
                .header("Authorization", owner.authorizationHeader()))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateGlobalExercise_isForbiddenAsNotFound() throws Exception {
    RegisteredUser user = registerUser("exglobal@example.com", "supersecret1", "Global Tester");

    String listResponse =
        mockMvc
            .perform(
                get("/api/exercises")
                    .header("Authorization", user.authorizationHeader())
                    .param("query", "bench press"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String globalId = objectMapper.readTree(listResponse).get("content").get(0).get("id").asText();

    mockMvc
        .perform(
            put("/api/exercises/" + globalId)
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new UpdateExerciseRequest(
                            "Hijacked Global", ExerciseCategory.CHEST, null, null, null))))
        .andExpect(status().isNotFound());
  }

  @Test
  void createExercise_withBlankName_returns400() throws Exception {
    RegisteredUser user =
        registerUser("exvalidation@example.com", "supersecret1", "Validation Tester");

    mockMvc
        .perform(
            post("/api/exercises")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateExerciseRequest("  ", ExerciseCategory.CHEST, null, null, null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }
}
