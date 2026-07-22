package com.gymsync.api.notesimport.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsync.api.common.exception.BadRequestException;
import com.gymsync.api.notesimport.config.LlmProperties;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Optional OpenAI-compatible chat-completions parser. Enabled only when {@code
 * gymsync.llm.provider=openai}. Validates all model output as untrusted input.
 */
@Component
@ConditionalOnProperty(prefix = "gymsync.llm", name = "provider", havingValue = "openai")
public class LlmWorkoutNotesParser implements WorkoutNotesParser {

  private static final Logger log = LoggerFactory.getLogger(LlmWorkoutNotesParser.class);

  private final LlmProperties properties;
  private final ObjectMapper objectMapper;
  private final String systemPrompt;
  private final HttpClient httpClient;

  public LlmWorkoutNotesParser(
      LlmProperties properties,
      ObjectMapper objectMapper,
      @Value("classpath:prompts/workout-notes-import.txt") Resource promptResource)
      throws Exception {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
            .build();
  }

  @Override
  public ParsedWorkout parse(String rawText) {
    if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
      throw new BadRequestException("LLM provider is configured but LLM_API_KEY is missing.");
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
                                  .put("content", rawText))));
    } catch (Exception ex) {
      throw new BadRequestException("Failed to build LLM request.");
    }

    Exception last = null;
    int attempts = 1 + Math.max(0, properties.getMaxRetries());
    for (int i = 0; i < attempts; i++) {
      try {
        log.info(
            "Calling LLM provider for notes import. url={} model={} attempt={}/{}",
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
            "LLM provider responded for notes import. url={} status={}",
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
          return parseProviderResponse(response.body());
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

  ParsedWorkout parseProviderResponse(String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      String content = root.path("choices").path(0).path("message").path("content").asText(null);
      if (content == null || content.isBlank()) {
        throw new BadRequestException("LLM response missing content.");
      }
      content = stripCodeFence(content.trim());
      JsonNode parsed = objectMapper.readTree(content);
      JsonNode exercises = parsed.path("exercises");
      if (!exercises.isArray() || exercises.isEmpty()) {
        throw new BadRequestException("LLM response missing exercises array.");
      }
      if (exercises.size() > properties.getMaxExercises()) {
        throw new BadRequestException("LLM response contained too many exercises.");
      }
      List<ParsedSet> sets = new ArrayList<>();
      for (JsonNode exercise : exercises) {
        String name = exercise.path("name").asText(null);
        if (name == null || name.isBlank()) {
          throw new BadRequestException("LLM exercise missing name.");
        }
        JsonNode setNodes = exercise.path("sets");
        if (!setNodes.isArray() || setNodes.isEmpty()) {
          throw new BadRequestException("LLM exercise missing sets.");
        }
        if (setNodes.size() > properties.getMaxSetsPerExercise()) {
          throw new BadRequestException("LLM exercise had too many sets.");
        }
        for (JsonNode setNode : setNodes) {
          if (!setNode.has("weight") || !setNode.has("reps") || !setNode.has("setNumber")) {
            throw new BadRequestException("LLM set missing required fields.");
          }
          BigDecimal weight = setNode.get("weight").decimalValue();
          int reps = setNode.get("reps").asInt();
          int setNumber = setNode.get("setNumber").asInt();
          if (weight.compareTo(BigDecimal.ZERO) < 0 || reps <= 0 || setNumber <= 0) {
            throw new BadRequestException("LLM set had invalid numeric values.");
          }
          sets.add(new ParsedSet(name, weight, reps, setNumber));
        }
      }
      return new ParsedWorkout(List.copyOf(sets));
    } catch (BadRequestException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BadRequestException("Malformed LLM output.");
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
