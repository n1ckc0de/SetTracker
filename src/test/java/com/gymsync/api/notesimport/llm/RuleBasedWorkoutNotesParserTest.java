package com.gymsync.api.notesimport.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymsync.api.common.exception.BadRequestException;
import com.gymsync.api.notesimport.config.LlmProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuleBasedWorkoutNotesParserTest {

  private RuleBasedWorkoutNotesParser parser;

  @BeforeEach
  void setUp() {
    LlmProperties properties = new LlmProperties();
    properties.setMaxExercises(20);
    properties.setMaxSetsPerExercise(20);
    parser = new RuleBasedWorkoutNotesParser(properties);
  }

  @Test
  void parse_setsRepsWeightFormat_expandsToOneSetPerRep() {
    ParsedWorkout workout = parser.parse("Bench 3x8 135 lb");

    assertThat(workout.sets()).hasSize(3);
    assertThat(workout.sets())
        .allSatisfy(
            set -> {
              assertThat(set.exerciseName()).isEqualTo("Bench");
              assertThat(set.weight()).isEqualTo(new BigDecimal("135"));
              assertThat(set.reps()).isEqualTo(8);
            });
    assertThat(workout.sets().stream().map(ParsedSet::setNumber).toList()).containsExactly(1, 2, 3);
  }

  @Test
  void parse_weightXRepsFormat_producesSingleSet() {
    ParsedWorkout workout = parser.parse("Deadlift 225x5");

    assertThat(workout.sets()).hasSize(1);
    ParsedSet set = workout.sets().get(0);
    assertThat(set.exerciseName()).isEqualTo("Deadlift");
    assertThat(set.weight()).isEqualTo(new BigDecimal("225"));
    assertThat(set.reps()).isEqualTo(5);
    assertThat(set.setNumber()).isEqualTo(1);
  }

  @Test
  void parse_multipleExercisesSeparatedByCommas_parsesEachIndependently() {
    ParsedWorkout workout = parser.parse("Bench 3x8 135 lb, Squat 5x5 185 lb");

    assertThat(workout.sets()).hasSize(8);
    assertThat(workout.sets().stream().map(ParsedSet::exerciseName).distinct().toList())
        .containsExactlyInAnyOrder("Bench", "Squat");
  }

  @Test
  void parse_multipleExercisesSeparatedByNewlines_parsesEachIndependently() {
    ParsedWorkout workout = parser.parse("Bench 3x8 135 lb\nSquat 5x5 185 lb");

    assertThat(workout.sets()).hasSize(8);
  }

  @Test
  void parse_blankInput_throwsBadRequest() {
    assertThatThrownBy(() -> parser.parse("   ")).isInstanceOf(BadRequestException.class);
  }

  @Test
  void parse_nullInput_throwsBadRequest() {
    assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(BadRequestException.class);
  }

  @Test
  void parse_unparsableSegment_throwsBadRequest() {
    assertThatThrownBy(() -> parser.parse("this is not a workout note"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Could not parse");
  }

  @Test
  void parse_tooManyExercises_throwsBadRequest() {
    LlmProperties properties = new LlmProperties();
    properties.setMaxExercises(1);
    properties.setMaxSetsPerExercise(20);
    RuleBasedWorkoutNotesParser strictParser = new RuleBasedWorkoutNotesParser(properties);

    assertThatThrownBy(() -> strictParser.parse("Bench 3x8 135 lb, Squat 5x5 185 lb"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Too many exercises");
  }

  @Test
  void parse_tooManySetsForExercise_throwsBadRequest() {
    LlmProperties properties = new LlmProperties();
    properties.setMaxExercises(20);
    properties.setMaxSetsPerExercise(2);
    RuleBasedWorkoutNotesParser strictParser = new RuleBasedWorkoutNotesParser(properties);

    assertThatThrownBy(() -> strictParser.parse("Bench 3x8 135 lb"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Too many sets");
  }
}
