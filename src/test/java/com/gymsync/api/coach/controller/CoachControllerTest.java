package com.gymsync.api.coach.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymsync.api.AbstractIntegrationTest;
import com.gymsync.api.coach.domain.ConfidenceLevel;
import com.gymsync.api.coach.domain.RecommendationType;
import com.gymsync.api.coach.llm.CoachRecommendationGenerator;
import com.gymsync.api.coach.llm.GeneratedCoachAdvice;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

class CoachControllerTest extends AbstractIntegrationTest {

  @MockBean private CoachRecommendationGenerator recommendationGenerator;

  @Test
  void generateListAcceptModifyDismiss_ownerScoped() throws Exception {
    RegisteredUser user = registerUser("coach-http@example.com", "supersecret1", "Coach");
    RegisteredUser other = registerUser("coach-other@example.com", "supersecret1", "Other");

    when(recommendationGenerator.generate(any()))
        .thenReturn(
            new GeneratedCoachAdvice(
                RecommendationType.GENERAL,
                "{}",
                "{\"action\":\"MAINTAIN\"}",
                "Keep training steady.",
                ConfidenceLevel.LOW));

    String exerciseId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        get("/api/exercises")
                            .header("Authorization", user.authorizationHeader())
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("content")
            .get(0)
            .path("id")
            .asText();

    mockMvc
        .perform(
            post("/api/coach/recommendations/generate").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    String created =
        mockMvc
            .perform(
                post("/api/coach/recommendations/generate")
                    .header("Authorization", user.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("exerciseId", exerciseId))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PROPOSED"))
            .andExpect(jsonPath("$.reasoning").value("Keep training steady."))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = objectMapper.readTree(created).get("id").asText();

    mockMvc
        .perform(
            get("/api/coach/recommendations")
                .header("Authorization", user.authorizationHeader())
                .param("status", "PROPOSED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id));

    mockMvc
        .perform(
            get("/api/coach/recommendations").header("Authorization", other.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    mockMvc
        .perform(
            patch("/api/coach/recommendations/" + id + "/accept")
                .header("Authorization", other.authorizationHeader()))
        .andExpect(status().isNotFound());

    // Fresh proposed for modify/dismiss paths
    when(recommendationGenerator.generate(any()))
        .thenReturn(
            new GeneratedCoachAdvice(
                RecommendationType.WEIGHT_ADJUSTMENT,
                "{}",
                "{\"weightDelta\":5}",
                "Nudge up.",
                ConfidenceLevel.MEDIUM));
    String created2 =
        mockMvc
            .perform(
                post("/api/coach/recommendations/generate")
                    .header("Authorization", user.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id2 = objectMapper.readTree(created2).get("id").asText();

    mockMvc
        .perform(
            patch("/api/coach/recommendations/" + id2 + "/modify")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("proposedChange", "{\"weightDelta\":2}", "reasoning", "Tweaked"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MODIFIED"));

    String created3 =
        mockMvc
            .perform(
                post("/api/coach/recommendations/generate")
                    .header("Authorization", user.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id3 = objectMapper.readTree(created3).get("id").asText();

    mockMvc
        .perform(
            patch("/api/coach/recommendations/" + id3 + "/dismiss")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISMISSED"));

    mockMvc
        .perform(
            patch("/api/coach/recommendations/" + id + "/accept")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"));
  }

  @Test
  void chatWithInjuryKeywords_returnsSafetyHold_andNeverCallsGenerator() throws Exception {
    RegisteredUser user = registerUser("coach-injury@example.com", "supersecret1", "Injury");

    mockMvc
        .perform(
            post("/api/coach/chat")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("message", "I have sharp pain in my shoulder"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.recommendationType").value("SAFETY_HOLD"))
        .andExpect(jsonPath("$.reasoning").value(org.hamcrest.Matchers.containsString("medical")));

    verify(recommendationGenerator, never()).generate(any());
  }

  @Test
  void chatWithoutInjury_callsGenerator() throws Exception {
    RegisteredUser user = registerUser("coach-chat@example.com", "supersecret1", "Chat");
    when(recommendationGenerator.generate(any()))
        .thenReturn(
            new GeneratedCoachAdvice(
                RecommendationType.GENERAL,
                "{}",
                "{\"action\":\"MAINTAIN\"}",
                "Ease in today",
                ConfidenceLevel.LOW));

    mockMvc
        .perform(
            post("/api/coach/chat")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("message", "feeling a bit tired"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.recommendationType").value("GENERAL"));

    verify(recommendationGenerator).generate(any());
  }

  @Test
  void chatOneRmQuestion_matchesStrengthEstimateEndpoint() throws Exception {
    RegisteredUser user = registerUser("coach-1rm@example.com", "supersecret1", "One RM");
    String chatMessage = "What is my 1rm for 225 x 5 reps?";

    String strengthResponse =
        mockMvc
            .perform(
                post("/api/strength/estimate-1rm")
                    .header("Authorization", user.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"weight\":225,\"reps\":5}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String chatResponse =
        mockMvc
            .perform(
                post("/api/coach/chat")
                    .header("Authorization", user.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("message", chatMessage))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    var strength = objectMapper.readTree(strengthResponse);
    var chat = objectMapper.readTree(chatResponse);
    var change = objectMapper.readTree(chat.get("proposedChange").asText());

    org.assertj.core.api.Assertions.assertThat(change.path("action").asText())
        .isEqualTo("ESTIMATE_1RM");
    org.assertj.core.api.Assertions.assertThat(change.get("workingEstimate").decimalValue())
        .isEqualByComparingTo(strength.get("workingEstimate").decimalValue());
    org.assertj.core.api.Assertions.assertThat(change.get("epley").decimalValue())
        .isEqualByComparingTo(strength.get("epley").decimalValue());
    org.assertj.core.api.Assertions.assertThat(change.get("brzycki").decimalValue())
        .isEqualByComparingTo(strength.get("brzycki").decimalValue());

    verify(recommendationGenerator, never()).generate(any());
  }

  @Test
  void applyPreviewAndApply_updateOnlyUncompletedSets() throws Exception {
    RegisteredUser user = registerUser("coach-apply@example.com", "supersecret1", "Apply Coach");
    String exerciseId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        get("/api/exercises")
                            .header("Authorization", user.authorizationHeader())
                            .param("query", "squat"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("content")
            .get(0)
            .path("id")
            .asText();

    String workoutId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/workouts").header("Authorization", user.authorizationHeader()))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();

    when(recommendationGenerator.generate(any()))
        .thenReturn(
            new GeneratedCoachAdvice(
                RecommendationType.WEIGHT_ADJUSTMENT,
                "{}",
                "{\"proposedWeight\":105,\"proposedSets\":3,\"proposedRepRangeMin\":6,\"proposedRepRangeMax\":8,\"targetRir\":2}",
                "Apply load",
                ConfidenceLevel.MEDIUM));

    String recId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/coach/recommendations/generate")
                            .header("Authorization", user.authorizationHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(Map.of("exerciseId", exerciseId))))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();

    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"exerciseId":"%s","enteredWeight":100,"weightEntryMode":"TOTAL","reps":8,"setNumber":1,"completed":true}
                    """
                        .formatted(exerciseId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"exerciseId":"%s","enteredWeight":100,"weightEntryMode":"TOTAL","reps":8,"setNumber":2,"completed":false}
                    """
                        .formatted(exerciseId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/coach/recommendations/" + recId + "/apply-preview")
                .header("Authorization", user.authorizationHeader())
                .param("workoutId", workoutId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sets[0].willChange").value(false))
        .andExpect(jsonPath("$.sets[1].willChange").value(true))
        .andExpect(jsonPath("$.sets[1].proposedWeight").value(105));

    mockMvc
        .perform(
            post("/api/coach/recommendations/" + recId + "/apply")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("workoutId", workoutId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].weight").value(105))
        .andExpect(jsonPath("$[0].setNumber").value(2));

    mockMvc
        .perform(
            get("/api/workouts/" + workoutId + "/sets")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].weight").value(100))
        .andExpect(jsonPath("$[1].weight").value(105));
  }
}
