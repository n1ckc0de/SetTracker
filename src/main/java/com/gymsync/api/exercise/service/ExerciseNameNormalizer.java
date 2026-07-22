package com.gymsync.api.exercise.service;

import java.util.Locale;

/**
 * Normalizes exercise names for matching/duplicate detection: lower-cases, strips punctuation
 * (keeping letters, digits, whitespace, and hyphens so names like "Pull-Up" normalize predictably
 * to "pull-up"), and collapses runs of whitespace into single spaces.
 */
public final class ExerciseNameNormalizer {

  private ExerciseNameNormalizer() {}

  public static String normalize(String rawName) {
    if (rawName == null) {
      return "";
    }
    String lower = rawName.toLowerCase(Locale.ROOT).trim();
    String withoutPunctuation = lower.replaceAll("[^a-z0-9\\s-]", "");
    return withoutPunctuation.trim().replaceAll("\\s+", " ");
  }
}
