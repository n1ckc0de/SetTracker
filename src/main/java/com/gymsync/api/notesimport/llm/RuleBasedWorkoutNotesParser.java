package com.gymsync.api.notesimport.llm;

import com.gymsync.api.common.exception.BadRequestException;
import com.gymsync.api.notesimport.config.LlmProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic parser for local development and tests. Supports patterns such as:
 *
 * <ul>
 *   <li>{@code Bench 3x8 135 lb}
 *   <li>{@code Squat 5x5 185}
 *   <li>{@code Deadlift 225x5}
 * </ul>
 *
 * Multiple exercises may be separated by commas or newlines.
 */
@Component
@ConditionalOnProperty(
    prefix = "gymsync.llm",
    name = "provider",
    havingValue = "rule-based",
    matchIfMissing = true)
public class RuleBasedWorkoutNotesParser implements WorkoutNotesParser {

  private static final Pattern SETS_REPS_WEIGHT =
      Pattern.compile(
          "(?i)^\\s*(.+?)\\s+(\\d+)\\s*[x×]\\s*(\\d+)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:lb|lbs|kg)?\\s*$");
  private static final Pattern WEIGHT_X_REPS =
      Pattern.compile(
          "(?i)^\\s*(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s*[x×]\\s*(\\d+)\\s*(?:lb|lbs|kg)?\\s*$");

  private final LlmProperties properties;

  public RuleBasedWorkoutNotesParser(LlmProperties properties) {
    this.properties = properties;
  }

  @Override
  public ParsedWorkout parse(String rawText) {
    if (rawText == null || rawText.isBlank()) {
      throw new BadRequestException("rawText must not be blank.");
    }
    String[] chunks = rawText.split("[,\\n;]+");
    List<ParsedSet> sets = new ArrayList<>();
    int exerciseCount = 0;
    for (String chunk : chunks) {
      String piece = chunk.trim();
      if (piece.isEmpty()) {
        continue;
      }
      exerciseCount++;
      if (exerciseCount > properties.getMaxExercises()) {
        throw new BadRequestException("Too many exercises in import.");
      }
      Matcher m1 = SETS_REPS_WEIGHT.matcher(piece);
      if (m1.matches()) {
        String name = m1.group(1).trim();
        int setCount = Integer.parseInt(m1.group(2));
        int reps = Integer.parseInt(m1.group(3));
        BigDecimal weight = new BigDecimal(m1.group(4));
        if (setCount > properties.getMaxSetsPerExercise()) {
          throw new BadRequestException("Too many sets for exercise: " + name);
        }
        for (int i = 1; i <= setCount; i++) {
          sets.add(new ParsedSet(name, weight, reps, i));
        }
        continue;
      }
      Matcher m2 = WEIGHT_X_REPS.matcher(piece);
      if (m2.matches()) {
        String name = m2.group(1).trim();
        BigDecimal weight = new BigDecimal(m2.group(2));
        int reps = Integer.parseInt(m2.group(3));
        sets.add(new ParsedSet(name, weight, reps, 1));
        continue;
      }
      throw new BadRequestException("Could not parse workout note segment: " + piece);
    }
    if (sets.isEmpty()) {
      throw new BadRequestException("No sets could be parsed from rawText.");
    }
    return new ParsedWorkout(List.copyOf(sets));
  }
}
