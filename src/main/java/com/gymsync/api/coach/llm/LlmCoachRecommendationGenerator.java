package com.gymsync.api.coach.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsync.api.coach.domain.ConfidenceLevel;
import com.gymsync.api.coach.domain.RecommendationType;
import com.gymsync.api.common.exception.BadRequestException;
import com.gymsync.api.notesimport.config.LlmProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * OpenAI-compatible coach generator. Enabled only when {@code gymsync.llm.provider=openai}. The
 * model must pick values within the bounds already present in {@link CoachContext}; {@link
 * CoachAdviceNormalizer} clamps/rejects anything outside.
 */
@Component
@ConditionalOnProperty(prefix = "gymsync.llm", name = "provider", havingValue = "openai")
public class LlmCoachRecommendationGenerator implements CoachRecommendationGenerator {

  private static final Logger log = LoggerFactory.getLogger(LlmCoachRecommendationGenerator.class);

  private final LlmProperties properties;
  private final ObjectMapper objectMapper;
  private final CoachAdviceNormalizer normalizer;
  private final String systemPrompt;
  private final HttpClient httpClient;

  public LlmCoachRecommendationGenerator(
      LlmProperties properties,
      ObjectMapper objectMapper,
      CoachAdviceNormalizer normalizer,
      @Value("classpath:prompts/coach-recommendation.txt") Resource promptResource)
      throws Exception {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.normalizer = normalizer;
    this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
            .build();
  }

  @Override
  public GeneratedCoachAdvice generate(CoachContext context) {
    if (context.safetyHold()) {
      return normalizer.safetyHoldAdvice(normalizer.buildPreviousPerformance(context));
    }
    if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
      throw new BadRequestException("LLM provider is configured but LLM_API_KEY is missing.");
    }

    String userPayload;
    try {
      userPayload = objectMapper.writeValueAsString(context);
    } catch (Exception ex) {
      throw new BadRequestException("Failed to build coach LLM request.");
    }

    String body;
    try {
      body =
          objectMapper.writeValueAsString(
              objectMapper
                  .createObjectNode()
                  .put("model", properties.getModel())
                  .put("temperature", 0)
                  .set(
                      "messages",
                      objectMapper
                          .createArrayNode()
                          .add(
                              objectMapper
                                  .createObjectNode()
                                  .put("role", "system")
                                  .put("content", systemPrompt))
                          .add(
                              objectMapper
                                  .createObjectNode()
                                  .put("role", "user")
                                  .put("content", userPayload))));
    } catch (Exception ex) {
      throw new BadRequestException("Failed to build coach LLM request.");
    }

    Exception last = null;
    int attempts = 1 + Math.max(0, properties.getMaxRetries());
    for (int i = 0; i < attempts; i++) {
      try {
        log.info(
            "Calling LLM provider for coach recommendation. url={} model={} attempt={}/{}",
            properties.getApiUrl(),
            properties.getModel(),
            i + 1,
            attempts);
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(properties.getApiUrl()))
                .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info(
            "LLM provider responded for coach recommendation. url={} status={}",
            properties.getApiUrl(),
            response.statusCode());
        if (response.statusCode() >= 400) {
          // Never log the Authorization header or api-key — status/body from the provider are
          // not secrets and are the only way to diagnose a rejected request.
          log.warn(
              "LLM provider returned an error response. url={} status={} body={}",
              properties.getApiUrl(),
              response.statusCode(),
              response.body());
          throw new BadRequestException("LLM provider returned an error.");
        }
        try {
          return normalizer.normalize(parseProviderResponse(response.body()), context);
        } catch (RuntimeException ex) {
          log.warn(
              "LLM provider returned 200 but its content could not be parsed. url={} status={}"
                  + " body={}",
              properties.getApiUrl(),
              response.statusCode(),
              response.body());
          throw ex;
        }
      } catch (BadRequestException ex) {
        throw ex;
      } catch (Exception ex) {
        log.warn(
            "LLM provider call failed (attempt {}/{}). url={} error={}",
            i + 1,
            attempts,
            properties.getApiUrl(),
            ex.toString());
        last = ex;
      }
    }
    throw new BadRequestException(
        "LLM provider is unavailable." + (last != null ? " " + last.getMessage() : ""));
  }

  GeneratedCoachAdvice parseProviderResponse(String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      String content = root.path("choices").path(0).path("message").path("content").asText(null);
      if (content == null || content.isBlank()) {
        throw new BadRequestException("LLM response missing content.");
      }
      content = stripCodeFence(content.trim());
      JsonNode parsed = objectMapper.readTree(content);
      String typeText = parsed.path("recommendationType").asText(null);
      if (typeText == null || typeText.isBlank()) {
        throw new BadRequestException("LLM response missing recommendationType.");
      }
      RecommendationType type;
      try {
        type = RecommendationType.valueOf(typeText);
      } catch (IllegalArgumentException ex) {
        throw new BadRequestException("LLM response had invalid recommendationType.");
      }
      if (type == RecommendationType.SAFETY_HOLD) {
        throw new BadRequestException(
            "LLM must not emit SAFETY_HOLD; that path is application-owned.");
      }
      JsonNode change = parsed.get("proposedChange");
      if (change == null || change.isNull()) {
        throw new BadRequestException("LLM response missing proposedChange.");
      }
      String proposedChange =
          change.isTextual() ? change.asText() : objectMapper.writeValueAsString(change);
      String reasoning = parsed.path("reasoning").asText(null);
      if (reasoning == null || reasoning.isBlank()) {
        throw new BadRequestException("LLM response missing reasoning.");
      }
      String confidenceText = parsed.path("confidenceLevel").asText(null);
      if (confidenceText == null || confidenceText.isBlank()) {
        throw new BadRequestException("LLM response missing confidenceLevel.");
      }
      ConfidenceLevel confidence;
      try {
        confidence = ConfidenceLevel.valueOf(confidenceText);
      } catch (IllegalArgumentException ex) {
        throw new BadRequestException("LLM response had invalid confidenceLevel.");
      }
      String previous =
          parsed.has("previousPerformance")
              ? (parsed.get("previousPerformance").isTextual()
                  ? parsed.get("previousPerformance").asText()
                  : objectMapper.writeValueAsString(parsed.get("previousPerformance")))
              : null;
      return new GeneratedCoachAdvice(type, previous, proposedChange, reasoning, confidence);
    } catch (BadRequestException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BadRequestException("Malformed LLM coach output.");
    }
  }

  private static String stripCodeFence(String content) {
    if (content.startsWith("```")) {
      int firstNl = content.indexOf('\n');
      int lastFence = content.lastIndexOf("```");
      if (firstNl > 0 && lastFence > firstNl) {
        return content.substring(firstNl + 1, lastFence).trim();
      }
    }
    return content;
  }
}
