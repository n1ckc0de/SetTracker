package com.gymsync.api.exercise.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymsync.api.AbstractIntegrationTest;
import com.gymsync.api.exercise.domain.ExerciseCategory;
import com.gymsync.api.exercise.dto.CreateExerciseRequest;
import com.gymsync.api.exercise.media.MediaSearchClient;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

class ExerciseMediaSuggestionControllerTest extends AbstractIntegrationTest {

  @MockBean private MediaSearchClient mediaSearchClient;

  @Test
  void generateCurated_createsPendingWithoutMutatingExercise_thenConfirmAppliesMedia()
      throws Exception {
    when(mediaSearchClient.findWikimediaImage(anyString())).thenReturn(Optional.empty());
    when(mediaSearchClient.findYouTubeDemo(anyString())).thenReturn(Optional.empty());

    RegisteredUser user = registerUser("mediasug@example.com", "supersecret1", "Media User");

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
            get("/api/exercises/" + exerciseId).header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.demoVideoUrl").value(nullValue()))
        .andExpect(jsonPath("$.thumbnailUrl").value(nullValue()));

    String generateResponse =
        mockMvc
            .perform(
                post("/api/exercises/" + exerciseId + "/media-suggestions")
                    .header("Authorization", user.authorizationHeader()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].source").value("CURATED"))
            .andExpect(jsonPath("$[0].confidence").value("HIGH"))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
            .andExpect(jsonPath("$[0].demoVideoUrl").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    mockMvc
        .perform(
            get("/api/exercises/" + exerciseId).header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.demoVideoUrl").value(nullValue()))
        .andExpect(jsonPath("$.mediaAttribution").value(nullValue()));

    String suggestionId = objectMapper.readTree(generateResponse).get(0).get("id").asText();

    mockMvc
        .perform(
            get("/api/exercises/" + exerciseId + "/media-suggestions")
                .header("Authorization", user.authorizationHeader())
                .param("status", "PENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(suggestionId));

    mockMvc
        .perform(
            patch(
                    "/api/exercises/"
                        + exerciseId
                        + "/media-suggestions/"
                        + suggestionId
                        + "/confirm")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));

    mockMvc
        .perform(
            get("/api/exercises/" + exerciseId).header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.demoVideoUrl").isNotEmpty())
        .andExpect(jsonPath("$.mediaAttribution").isNotEmpty());
  }

  @Test
  void confirmCustomExercise_requiresOwner() throws Exception {
    when(mediaSearchClient.findWikimediaImage(anyString())).thenReturn(Optional.empty());
    when(mediaSearchClient.findYouTubeDemo(anyString())).thenReturn(Optional.empty());

    RegisteredUser owner = registerUser("mediaowner@example.com", "supersecret1", "Owner");
    RegisteredUser other = registerUser("mediaother@example.com", "supersecret1", "Other");

    String createResponse =
        mockMvc
            .perform(
                post("/api/exercises")
                    .header("Authorization", owner.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreateExerciseRequest(
                                "Romanian Deadlift", ExerciseCategory.LEGS, null, null, null))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String exerciseId = objectMapper.readTree(createResponse).get("id").asText();

    String generateResponse =
        mockMvc
            .perform(
                post("/api/exercises/" + exerciseId + "/media-suggestions")
                    .header("Authorization", owner.authorizationHeader()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].source").value("CURATED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String suggestionId = objectMapper.readTree(generateResponse).get(0).get("id").asText();

    mockMvc
        .perform(
            patch(
                    "/api/exercises/"
                        + exerciseId
                        + "/media-suggestions/"
                        + suggestionId
                        + "/confirm")
                .header("Authorization", other.authorizationHeader()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            patch(
                    "/api/exercises/"
                        + exerciseId
                        + "/media-suggestions/"
                        + suggestionId
                        + "/confirm")
                .header("Authorization", owner.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  @Test
  void generate_includesWikimediaWhenClientReturnsImage_andRejectWorks() throws Exception {
    when(mediaSearchClient.findWikimediaImage(anyString()))
        .thenReturn(
            Optional.of(
                new MediaSearchClient.WikimediaImageResult(
                    "https://upload.wikimedia.org/wikipedia/commons/example.jpg",
                    "File:Example.jpg")));
    when(mediaSearchClient.findYouTubeDemo(anyString())).thenReturn(Optional.empty());

    RegisteredUser user = registerUser("mediawiki@example.com", "supersecret1", "Wiki User");

    String listResponse =
        mockMvc
            .perform(
                get("/api/exercises")
                    .header("Authorization", user.authorizationHeader())
                    .param("query", "Squat")
                    .param("size", "5"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String exerciseId = null;
    for (JsonNode node : objectMapper.readTree(listResponse).get("content")) {
      if ("Squat".equals(node.get("name").asText())) {
        exerciseId = node.get("id").asText();
        break;
      }
    }
    org.assertj.core.api.Assertions.assertThat(exerciseId).isNotBlank();

    String generateResponse =
        mockMvc
            .perform(
                post("/api/exercises/" + exerciseId + "/media-suggestions")
                    .header("Authorization", user.authorizationHeader()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String wikiSuggestionId = null;
    for (JsonNode node : objectMapper.readTree(generateResponse)) {
      if ("WIKIMEDIA".equals(node.get("source").asText())) {
        wikiSuggestionId = node.get("id").asText();
        org.assertj.core.api.Assertions.assertThat(node.get("mediaAttribution").asText())
            .contains("via Wikimedia Commons");
      }
    }
    org.assertj.core.api.Assertions.assertThat(wikiSuggestionId).isNotBlank();

    mockMvc
        .perform(
            patch(
                    "/api/exercises/"
                        + exerciseId
                        + "/media-suggestions/"
                        + wikiSuggestionId
                        + "/reject")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
  }
}
