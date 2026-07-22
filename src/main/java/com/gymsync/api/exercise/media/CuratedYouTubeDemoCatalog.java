package com.gymsync.api.exercise.media;

import com.gymsync.api.exercise.service.ExerciseNameNormalizer;
import java.util.Map;
import java.util.Optional;

/**
 * Hardcoded reputable YouTube demo video IDs for common seeded exercise names. Used when generating
 * curated PENDING suggestions (no auto-publish).
 */
public final class CuratedYouTubeDemoCatalog {

  private static final String CURATED_ATTRIBUTION =
      "Suggested YouTube demonstration (pending review). Embed-only; content remains on YouTube.";

  private static final Map<String, String> VIDEO_IDS_BY_NORMALIZED_NAME =
      Map.ofEntries(
          Map.entry("bench press", "4Y2ZdHCOXok"),
          Map.entry("squat", "ultWZbUMPL8"),
          Map.entry("deadlift", "op9kVnSso6Q"),
          Map.entry("overhead press", "2yjwXTZQDDI"),
          Map.entry("barbell row", "FWJR5Ve8bnQ"),
          Map.entry("pull-up", "eGo4IYlbE5g"),
          Map.entry("lat pulldown", "CAwf7n6Luuc"),
          Map.entry("romanian deadlift", "jEy_czb3RKA"),
          Map.entry("leg press", "IZxyjW7MPJQ"),
          Map.entry("dumbbell curl", "ykJmrZ5v0Oo"),
          Map.entry("triceps pushdown", "2-LAMcpzODU"),
          Map.entry("plank", "ASdvN_XEl_c"));

  private CuratedYouTubeDemoCatalog() {}

  public static Optional<CuratedDemo> findByExerciseName(String exerciseName) {
    String key = ExerciseNameNormalizer.normalize(exerciseName);
    String videoId = VIDEO_IDS_BY_NORMALIZED_NAME.get(key);
    if (videoId == null) {
      return Optional.empty();
    }
    return Optional.of(
        new CuratedDemo("https://www.youtube.com/watch?v=" + videoId, CURATED_ATTRIBUTION));
  }

  public record CuratedDemo(String demoVideoUrl, String mediaAttribution) {}
}
