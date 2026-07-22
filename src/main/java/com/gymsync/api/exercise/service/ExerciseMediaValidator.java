package com.gymsync.api.exercise.service;

import com.gymsync.api.common.exception.BadRequestException;
import java.net.URI;

/** Validates optional external media URLs without performing network reachability checks. */
public final class ExerciseMediaValidator {

  private ExerciseMediaValidator() {}

  public static void validate(String thumbnailUrl, String demoVideoUrl, String mediaAttribution) {
    String thumb = blankToNull(thumbnailUrl);
    String demo = blankToNull(demoVideoUrl);
    String attribution = blankToNull(mediaAttribution);

    if (thumb != null) {
      requireHttpUrl(thumb, "thumbnailUrl");
    }
    if (demo != null) {
      requireHttpUrl(demo, "demoVideoUrl");
      if (attribution == null) {
        throw new BadRequestException("mediaAttribution is required when demoVideoUrl is set.");
      }
    }
  }

  public static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static void requireHttpUrl(String value, String field) {
    try {
      URI uri = URI.create(value);
      String scheme = uri.getScheme();
      if (scheme == null
          || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
          || uri.getHost() == null
          || uri.getHost().isBlank()) {
        throw new BadRequestException(field + " must be a valid http(s) URL.");
      }
    } catch (IllegalArgumentException ex) {
      throw new BadRequestException(field + " must be a valid http(s) URL.");
    }
  }
}
