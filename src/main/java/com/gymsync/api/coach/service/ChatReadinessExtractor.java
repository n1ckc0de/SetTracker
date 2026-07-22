package com.gymsync.api.coach.service;

import com.gymsync.api.readiness.dto.CreateReadinessRequest;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight free-text → readiness signal extraction for coach chat (rule-based, no LLM). */
public final class ChatReadinessExtractor {

  private static final Pattern SLEEP_HOURS =
      Pattern.compile("(?i)\\b(?:slept|sleep)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:h|hrs|hours)?\\b");

  private ChatReadinessExtractor() {}

  public static CreateReadinessRequest extract(String message, java.util.UUID workoutId) {
    if (message == null || message.isBlank()) {
      return new CreateReadinessRequest(workoutId, null, null, null, null, null, null);
    }
    String text = message.trim();
    String lower = text.toLowerCase(Locale.ROOT);

    BigDecimal sleepHours = null;
    Matcher sleepMatcher = SLEEP_HOURS.matcher(text);
    if (sleepMatcher.find()) {
      sleepHours = new BigDecimal(sleepMatcher.group(1));
    }

    Integer sleepQuality = null;
    if (containsAny(
        lower, "didn't sleep", "didnt sleep", "slept poorly", "bad sleep", "insomnia")) {
      sleepQuality = 2;
    } else if (containsAny(lower, "slept well", "good sleep", "well rested")) {
      sleepQuality = 4;
    }

    Integer energy = null;
    if (containsAny(lower, "exhausted", "low energy", "tired", "wiped")) {
      energy = 2;
    } else if (containsAny(lower, "feeling good", "high energy", "fresh")) {
      energy = 4;
    }

    Integer soreness = null;
    if (containsAny(lower, "very sore", "extremely sore")) {
      soreness = 5;
    } else if (containsAny(lower, "sore", "soreness", "tight")) {
      soreness = 4;
    }

    Integer stress = null;
    if (containsAny(lower, "stressed", "stressful", "anxious", "overwhelmed")) {
      stress = 4;
    }

    String note = text.length() > 500 ? text.substring(0, 500) : text;
    return new CreateReadinessRequest(
        workoutId, sleepHours, sleepQuality, energy, soreness, stress, note);
  }

  private static boolean containsAny(String haystack, String... needles) {
    for (String n : needles) {
      if (haystack.contains(n)) {
        return true;
      }
    }
    return false;
  }
}
