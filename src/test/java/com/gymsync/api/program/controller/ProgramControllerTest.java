package com.gymsync.api.program.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymsync.api.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ProgramControllerTest extends AbstractIntegrationTest {

  @Test
  void crud_ownershipBoundaries_andDayUniqueness_andStartDay() throws Exception {
    RegisteredUser owner = registerUser("prog-owner@example.com", "supersecret1", "Owner");
    RegisteredUser other = registerUser("prog-other@example.com", "supersecret1", "Other");

    String exerciseId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        get("/api/exercises")
                            .header("Authorization", owner.authorizationHeader())
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("content")
            .get(0)
            .path("id")
            .asText();

    String createBody =
        objectMapper.writeValueAsString(Map.of("name", "Push Pull", "description", "Simple split"));
    String programJson =
        mockMvc
            .perform(
                post("/api/programs")
                    .header("Authorization", owner.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Push Pull"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String programId = objectMapper.readTree(programJson).get("id").asText();

    mockMvc
        .perform(
            get("/api/programs/" + programId).header("Authorization", other.authorizationHeader()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            put("/api/programs/" + programId)
                .header("Authorization", other.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("name", "Hijack", "description", ""))))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            delete("/api/programs/" + programId)
                .header("Authorization", other.authorizationHeader()))
        .andExpect(status().isNotFound());

    Map<String, Object> dayBody =
        Map.of(
            "dayNumber",
            1,
            "name",
            "Push Day",
            "exercises",
            List.of(
                Map.of(
                    "exerciseId",
                    exerciseId,
                    "orderInDay",
                    1,
                    "targetSets",
                    3,
                    "targetRepsMin",
                    8,
                    "targetRepsMax",
                    10)));

    mockMvc
        .perform(
            post("/api/programs/" + programId + "/days")
                .header("Authorization", owner.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dayBody)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.dayNumber").value(1))
        .andExpect(jsonPath("$.exercises[0].exerciseId").value(exerciseId));

    mockMvc
        .perform(
            post("/api/programs/" + programId + "/days")
                .header("Authorization", owner.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dayBody)))
        .andExpect(status().isConflict());

    String startJson =
        mockMvc
            .perform(
                post("/api/programs/" + programId + "/days/1/start")
                    .header("Authorization", owner.authorizationHeader()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.workout.status").value("ACTIVE"))
            .andExpect(jsonPath("$.plannedExercises[0].exerciseId").value(exerciseId))
            .andExpect(jsonPath("$.plannedExercises[0].targetSets").value(3))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode start = objectMapper.readTree(startJson);
    // No fabricated sets: planned guidance only; workout itself has empty sets via detail.
    mockMvc
        .perform(
            get("/api/workouts/" + start.path("workout").path("id").asText())
                .header("Authorization", owner.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sets").isEmpty());

    mockMvc
        .perform(
            post("/api/programs/" + programId + "/days/1/start")
                .header("Authorization", owner.authorizationHeader()))
        .andExpect(status().isConflict());
  }

  @Test
  void mediaValidation_onExerciseCreate() throws Exception {
    RegisteredUser user = registerUser("media-user@example.com", "supersecret1", "Media");
    mockMvc
        .perform(
            post("/api/exercises")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Bad Demo","category":"CHEST","demoVideoUrl":"https://example.com/v"}
                    """))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/exercises")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Good Demo","category":"CHEST",
                     "demoVideoUrl":"https://example.com/v",
                     "mediaAttribution":"Demo via Example",
                     "thumbnailUrl":"https://example.com/t.jpg"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.demoVideoUrl").value("https://example.com/v"))
        .andExpect(jsonPath("$.mediaAttribution").value("Demo via Example"));
  }

  @Test
  void listUpdateDayDeleteDay_andOwnerUpdateProgram() throws Exception {
    RegisteredUser owner = registerUser("prog-list@example.com", "supersecret1", "List Owner");

    String exerciseId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        get("/api/exercises")
                            .header("Authorization", owner.authorizationHeader())
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("content")
            .get(0)
            .path("id")
            .asText();

    String programId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/programs")
                            .header("Authorization", owner.authorizationHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(
                                    Map.of("name", "Full Body", "description", "v1"))))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();

    mockMvc
        .perform(get("/api/programs").header("Authorization", owner.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(programId))
        .andExpect(jsonPath("$[0].name").value("Full Body"));

    mockMvc
        .perform(
            put("/api/programs/" + programId)
                .header("Authorization", owner.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "Full Body Renamed", "description", "v2"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Full Body Renamed"))
        .andExpect(jsonPath("$.description").value("v2"));

    Map<String, Object> dayBody =
        Map.of(
            "dayNumber",
            1,
            "name",
            "Day A",
            "exercises",
            List.of(
                Map.of(
                    "exerciseId",
                    exerciseId,
                    "orderInDay",
                    1,
                    "targetSets",
                    3,
                    "targetRepsMin",
                    8,
                    "targetRepsMax",
                    10)));

    mockMvc
        .perform(
            post("/api/programs/" + programId + "/days")
                .header("Authorization", owner.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dayBody)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            put("/api/programs/" + programId + "/days/1")
                .header("Authorization", owner.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name",
                            "Day A Updated",
                            "exercises",
                            List.of(
                                Map.of(
                                    "exerciseId",
                                    exerciseId,
                                    "orderInDay",
                                    1,
                                    "targetSets",
                                    4,
                                    "targetRepsMin",
                                    6,
                                    "targetRepsMax",
                                    8))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Day A Updated"))
        .andExpect(jsonPath("$.exercises[0].targetSets").value(4));

    mockMvc
        .perform(
            delete("/api/programs/" + programId + "/days/1")
                .header("Authorization", owner.authorizationHeader()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/programs/" + programId).header("Authorization", owner.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.days").isEmpty());
  }
}
