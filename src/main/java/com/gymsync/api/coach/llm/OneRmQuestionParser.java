package com.gymsync.api.coach.llm;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses natural-language 1RM questions into weight/reps inputs. */
public final class OneRmQuestionParser {

  private static final Pattern WEIGHT_REPS =
      Pattern.compile(
          "(?i)(\\d+(?:\\.\\d+)?)\\s*(?:lbs?|pounds?|kg|kilos?)?\\s*(?:x|for|@|\\*|by)\\s*(\\d+)\\s*reps?");

  private OneRmQuestionParser() {}

  public record ParsedOneRmQuestion(BigDecimal weight, int reps) {}

  public static Optional<ParsedOneRmQuestion> parse(String message) {
    if (message == null || message.isBlank()) {
      return Optional.empty();
    }
    String lower = message.toLowerCase();
    if (!lower.contains("1rm")
        && !lower.contains("one rep max")
        && !lower.contains("one-rep")
        && !lower.contains("max estimate")
        && !lower.contains("estimate my max")) {
      return Optional.empty();
    }
    Matcher matcher = WEIGHT_REPS.matcher(message);
    if (!matcher.find()) {
      return Optional.empty();
    }
    return Optional.of(
        new ParsedOneRmQuestion(
            new BigDecimal(matcher.group(1)), Integer.parseInt(matcher.group(2))));
  }
}
