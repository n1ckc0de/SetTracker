package com.gymsync.api.coach.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsync.api.coach.domain.ConfidenceLevel;
import com.gymsync.api.coach.domain.RecommendationType;
import com.gymsync.api.common.exception.BadRequestException;
import com.gymsync.api.notesimport.config.LlmProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class LlmCoachRecommendationGeneratorTest {

  private LlmCoachRecommendationGenerator generator;

  @BeforeEach
  void setUp() throws Exception {
    LlmProperties properties = new LlmProperties();
    properties.setApiKey("test-key");
    ObjectMapper mapper = new ObjectMapper();
    generator =
        new LlmCoachRecommendationGenerator(
            properties,
            mapper,
            new CoachAdviceNormalizer(mapper),
            new ByteArrayResource("system".getBytes()));
  }

  @Test
  void parseProviderResponse_rejectsMalformed() {
    String body =
        """
        {"choices":[{"message":{"content":"not json"}}]}
        """;
    assertThatThrownBy(() -> generator.parseProviderResponse(body))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void parseProviderResponse_rejectsSafetyHoldFromLlm() {
    String content =
        """
        {"recommendationType":"SAFETY_HOLD","proposedChange":{"action":"REST"},"reasoning":"x","confidenceLevel":"HIGH"}
        """;
    String body =
        "{\"choices\":[{\"message\":{\"content\":"
            + new ObjectMapper().valueToTree(content).toString()
            + "}}]}";
    assertThatThrownBy(() -> generator.parseProviderResponse(body))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void parseProviderResponse_acceptsValidPayload() throws Exception {
    String content =
        """
        {"recommendationType":"WEIGHT_ADJUSTMENT","proposedChange":{"weightDelta":-5},"reasoning":"Ease off","confidenceLevel":"MEDIUM"}
        """;
    String body =
        "{\"choices\":[{\"message\":{\"content\":"
            + new ObjectMapper().writeValueAsString(content)
            + "}}]}";
    GeneratedCoachAdvice advice = generator.parseProviderResponse(body);
    assertThat(advice.type()).isEqualTo(RecommendationType.WEIGHT_ADJUSTMENT);
    assertThat(advice.confidence()).isEqualTo(ConfidenceLevel.MEDIUM);
    assertThat(advice.proposedChangeJson()).contains("weightDelta");
  }
}
