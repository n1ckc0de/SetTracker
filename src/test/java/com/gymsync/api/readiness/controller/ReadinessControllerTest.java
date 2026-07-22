package com.gymsync.api.readiness.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymsync.api.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ReadinessControllerTest extends AbstractIntegrationTest {

  @Test
  void createLatestAndList_areOwnerScoped() throws Exception {
    RegisteredUser user = registerUser("ready-http@example.com", "supersecret1", "Ready");
    RegisteredUser other = registerUser("ready-other@example.com", "supersecret1", "Other");

    mockMvc
        .perform(
            post("/api/readiness")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("energyLevel", 4))))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/readiness")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "sleepHours",
                            7.5,
                            "sleepQuality",
                            4,
                            "energyLevel",
                            3,
                            "soreness",
                            2,
                            "stress",
                            1,
                            "note",
                            "feeling fine"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.energyLevel").value(3))
        .andExpect(jsonPath("$.note").value("feeling fine"));

    mockMvc
        .perform(get("/api/readiness/latest").header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sleepQuality").value(4));

    mockMvc
        .perform(get("/api/readiness/latest").header("Authorization", other.authorizationHeader()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/readiness").header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].energyLevel").value(3));

    mockMvc
        .perform(get("/api/readiness").header("Authorization", other.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void create_rejectsOutOfRangeScaleFields() throws Exception {
    RegisteredUser user = registerUser("ready-bad@example.com", "supersecret1", "Ready Bad");

    mockMvc
        .perform(
            post("/api/readiness")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("soreness", 6))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/readiness")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("energyLevel", 0))))
        .andExpect(status().isBadRequest());
  }
}
