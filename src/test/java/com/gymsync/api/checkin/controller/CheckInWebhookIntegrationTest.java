package com.gymsync.api.checkin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymsync.api.AbstractIntegrationTest;
import com.gymsync.api.checkin.service.WebhookSignatureValidator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * End-to-end coverage of {@code POST /api/webhooks/checkin}: signature validation, idempotency,
 * unknown users, and active-workout conflicts. See docs/WEBHOOKS.md.
 */
class CheckInWebhookIntegrationTest extends AbstractIntegrationTest {

  @Autowired private WebhookSignatureValidator signatureValidator;

  private String body(String eventId, UUID userId, String source) {
    return """
    {"eventId":"%s","userId":"%s","source":"%s"}
    """
        .formatted(eventId, userId, source)
        .strip();
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signedRequest(
      String body) {
    String timestamp = String.valueOf(Instant.now().getEpochSecond());
    String signature = signatureValidator.sign(timestamp, body.getBytes(StandardCharsets.UTF_8));
    return post("/api/webhooks/checkin")
        .contentType(MediaType.APPLICATION_JSON)
        .header(CheckInWebhookController.TIMESTAMP_HEADER, timestamp)
        .header(CheckInWebhookController.SIGNATURE_HEADER, signature)
        .content(body);
  }

  @Test
  void checkIn_validSignature_createsActiveWebhookWorkout() throws Exception {
    RegisteredUser user = registerUser("checkin1@example.com", "supersecret1", "Checkin One");
    String body = body("evt-checkin-1", user.id(), "gym-turnstile-mock");

    String response =
        mockMvc
            .perform(signedRequest(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PROCESSED"))
            .andExpect(jsonPath("$.workoutId", org.hamcrest.Matchers.notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String workoutId = objectMapper.readTree(response).get("workoutId").asText();

    mockMvc
        .perform(
            get("/api/workouts/" + workoutId).header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.source").value("WEBHOOK"));
  }

  @Test
  void checkIn_duplicateEventId_returnsDuplicateAndCreatesOnlyOneWorkout() throws Exception {
    RegisteredUser user = registerUser("checkin2@example.com", "supersecret1", "Checkin Two");
    String body = body("evt-checkin-2", user.id(), "gym-turnstile-mock");

    mockMvc.perform(signedRequest(body)).andExpect(status().isCreated());

    // Provider retries the same event id.
    mockMvc
        .perform(signedRequest(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DUPLICATE"));

    String listResponse =
        mockMvc
            .perform(
                get("/api/workouts")
                    .header("Authorization", user.authorizationHeader())
                    .param("source", "WEBHOOK"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(objectMapper.readTree(listResponse).get("content")).hasSize(1);
  }

  @Test
  void checkIn_unknownUser_returns404() throws Exception {
    String body = body("evt-checkin-unknown", UUID.randomUUID(), "gym-turnstile-mock");

    mockMvc.perform(signedRequest(body)).andExpect(status().isNotFound());
  }

  @Test
  void checkIn_userAlreadyHasActiveWorkout_returns409() throws Exception {
    RegisteredUser user = registerUser("checkin3@example.com", "supersecret1", "Checkin Three");
    mockMvc
        .perform(post("/api/workouts").header("Authorization", user.authorizationHeader()))
        .andExpect(status().isCreated());

    String body = body("evt-checkin-3", user.id(), "gym-turnstile-mock");
    mockMvc.perform(signedRequest(body)).andExpect(status().isConflict());
  }

  @Test
  void checkIn_invalidSignature_returns401() throws Exception {
    RegisteredUser user = registerUser("checkin4@example.com", "supersecret1", "Checkin Four");
    String body = body("evt-checkin-4", user.id(), "gym-turnstile-mock");
    String timestamp = String.valueOf(Instant.now().getEpochSecond());

    mockMvc
        .perform(
            post("/api/webhooks/checkin")
                .contentType(MediaType.APPLICATION_JSON)
                .header(CheckInWebhookController.TIMESTAMP_HEADER, timestamp)
                .header(CheckInWebhookController.SIGNATURE_HEADER, "0".repeat(64))
                .content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void checkIn_missingSignatureHeader_returns401() throws Exception {
    RegisteredUser user = registerUser("checkin5@example.com", "supersecret1", "Checkin Five");
    String body = body("evt-checkin-5", user.id(), "gym-turnstile-mock");
    String timestamp = String.valueOf(Instant.now().getEpochSecond());

    mockMvc
        .perform(
            post("/api/webhooks/checkin")
                .contentType(MediaType.APPLICATION_JSON)
                .header(CheckInWebhookController.TIMESTAMP_HEADER, timestamp)
                .content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void checkIn_expiredTimestamp_returns401() throws Exception {
    RegisteredUser user = registerUser("checkin6@example.com", "supersecret1", "Checkin Six");
    String body = body("evt-checkin-6", user.id(), "gym-turnstile-mock");
    String staleTimestamp = String.valueOf(Instant.now().minusSeconds(600).getEpochSecond());
    String signature =
        signatureValidator.sign(staleTimestamp, body.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            post("/api/webhooks/checkin")
                .contentType(MediaType.APPLICATION_JSON)
                .header(CheckInWebhookController.TIMESTAMP_HEADER, staleTimestamp)
                .header(CheckInWebhookController.SIGNATURE_HEADER, signature)
                .content(body))
        .andExpect(status().isUnauthorized());
  }
}
