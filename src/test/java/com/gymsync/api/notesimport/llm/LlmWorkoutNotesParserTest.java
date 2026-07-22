package com.gymsync.api.notesimport.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsync.api.common.exception.BadRequestException;
import com.gymsync.api.notesimport.config.LlmProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Exercises {@link LlmWorkoutNotesParser#parseProviderResponse} directly (package-visible),
 * covering malformed/hallucinated output that a real provider could plausibly return. All LLM
 * output must be treated as untrusted input (spec section 13).
 */
class LlmWorkoutNotesParserTest {

  private LlmWorkoutNotesParser parser;

  @BeforeEach
  void setUp() throws Exception {
    LlmProperties properties = new LlmProperties();
    properties.setMaxExercises(20);
    properties.setMaxSetsPerExercise(20);
    parser =
        new LlmWorkoutNotesParser(
            properties,
            new ObjectMapper(),
            new ClassPathResource("prompts/workout-notes-import.txt"));
  }

  private static String chatCompletionWithContent(String content) {
    String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    return "{\"choices\":[{\"message\":{\"content\":\"" + escaped + "\"}}]}";
  }

  @Test
  void parseProviderResponse_wellFormedContent_returnsSets() {
    String content =
        """
        {"exercises":[{"name":"Bench Press","sets":[
            {"weight":135,"reps":8,"setNumber":1},
            {"weight":135,"reps":8,"setNumber":2}
        ]}]}
        """;
    ParsedWorkout workout = parser.parseProviderResponse(chatCompletionWithContent(content));

    assertThat(workout.sets()).hasSize(2);
    assertThat(workout.sets().get(0).exerciseName()).isEqualTo("Bench Press");
    assertThat(workout.sets().get(0).reps()).isEqualTo(8);
  }

  @Test
  void parseProviderResponse_contentWrappedInMarkdownCodeFence_isStripped() {
    String content =
        "```json\n"
            + "{\"exercises\":[{\"name\":\"Squat\",\"sets\":[{\"weight\":185,\"reps\":5,\"setNumber\":1}]}]}\n"
            + "```";
    ParsedWorkout workout = parser.parseProviderResponse(chatCompletionWithContent(content));

    assertThat(workout.sets()).hasSize(1);
    assertThat(workout.sets().get(0).exerciseName()).isEqualTo("Squat");
  }

  @Test
  void parseProviderResponse_missingContent_throwsBadRequest() {
    assertThatThrownBy(() -> parser.parseProviderResponse("{\"choices\":[{\"message\":{}}]}"))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void parseProviderResponse_contentIsNotJson_throwsBadRequest() {
    assertThatThrownBy(
            () -> parser.parseProviderResponse(chatCompletionWithContent("not json at all")))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void parseProviderResponse_missingExercisesArray_throwsBadRequest() {
    String content = "{\"notes\":\"I forgot the schema\"}";

    assertThatThrownBy(() -> parser.parseProviderResponse(chatCompletionWithContent(content)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("exercises");
  }

  @Test
  void parseProviderResponse_emptyExercisesArray_throwsBadRequest() {
    String content = "{\"exercises\":[]}";

    assertThatThrownBy(() -> parser.parseProviderResponse(chatCompletionWithContent(content)))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void parseProviderResponse_exerciseMissingName_throwsBadRequest() {
    String content = "{\"exercises\":[{\"sets\":[{\"weight\":100,\"reps\":5,\"setNumber\":1}]}]}";

    assertThatThrownBy(() -> parser.parseProviderResponse(chatCompletionWithContent(content)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("name");
  }

  @Test
  void parseProviderResponse_exerciseMissingSets_throwsBadRequest() {
    String content = "{\"exercises\":[{\"name\":\"Bench\"}]}";

    assertThatThrownBy(() -> parser.parseProviderResponse(chatCompletionWithContent(content)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("sets");
  }

  @Test
  void parseProviderResponse_setMissingRequiredField_throwsBadRequest() {
    String content =
        "{\"exercises\":[{\"name\":\"Bench\",\"sets\":[{\"weight\":100,\"reps\":5}]}]}";

    assertThatThrownBy(() -> parser.parseProviderResponse(chatCompletionWithContent(content)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("required fields");
  }

  @Test
  void parseProviderResponse_negativeWeight_throwsBadRequest() {
    String content =
        "{\"exercises\":[{\"name\":\"Bench\",\"sets\":[{\"weight\":-10,\"reps\":5,\"setNumber\":1}]}]}";

    assertThatThrownBy(() -> parser.parseProviderResponse(chatCompletionWithContent(content)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("invalid numeric values");
  }

  @Test
  void parseProviderResponse_zeroReps_throwsBadRequest() {
    String content =
        "{\"exercises\":[{\"name\":\"Bench\",\"sets\":[{\"weight\":100,\"reps\":0,\"setNumber\":1}]}]}";

    assertThatThrownBy(() -> parser.parseProviderResponse(chatCompletionWithContent(content)))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void parseProviderResponse_tooManyExercises_throwsBadRequest() throws Exception {
    LlmProperties strictProperties = new LlmProperties();
    strictProperties.setMaxExercises(1);
    strictProperties.setMaxSetsPerExercise(20);
    LlmWorkoutNotesParser strictParser =
        new LlmWorkoutNotesParser(
            strictProperties,
            new ObjectMapper(),
            new ClassPathResource("prompts/workout-notes-import.txt"));
    String content =
        "{\"exercises\":["
            + "{\"name\":\"Bench\",\"sets\":[{\"weight\":100,\"reps\":5,\"setNumber\":1}]},"
            + "{\"name\":\"Squat\",\"sets\":[{\"weight\":100,\"reps\":5,\"setNumber\":1}]}"
            + "]}";

    assertThatThrownBy(() -> strictParser.parseProviderResponse(chatCompletionWithContent(content)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("too many exercises");
  }

  @Test
  void parse_whenApiKeyMissing_throwsBadRequest() {
    LlmProperties properties = new LlmProperties();
    properties.setApiKey(" ");
    properties.setMaxExercises(20);
    properties.setMaxSetsPerExercise(20);
    LlmWorkoutNotesParser missingKeyParser;
    try {
      missingKeyParser =
          new LlmWorkoutNotesParser(
              properties,
              new ObjectMapper(),
              new ClassPathResource("prompts/workout-notes-import.txt"));
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

    assertThatThrownBy(() -> missingKeyParser.parse("Bench 3x8 135"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("LLM_API_KEY");
  }

  @Test
  void parseProviderResponse_tooManySetsForExercise_throwsBadRequest() throws Exception {
    LlmProperties strictProperties = new LlmProperties();
    strictProperties.setMaxExercises(20);
    strictProperties.setMaxSetsPerExercise(1);
    LlmWorkoutNotesParser strictParser =
        new LlmWorkoutNotesParser(
            strictProperties,
            new ObjectMapper(),
            new ClassPathResource("prompts/workout-notes-import.txt"));
    String content =
        "{\"exercises\":[{\"name\":\"Bench\",\"sets\":["
            + "{\"weight\":100,\"reps\":5,\"setNumber\":1},"
            + "{\"weight\":100,\"reps\":5,\"setNumber\":2}"
            + "]}]}";

    assertThatThrownBy(() -> strictParser.parseProviderResponse(chatCompletionWithContent(content)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("too many sets");
  }
}
