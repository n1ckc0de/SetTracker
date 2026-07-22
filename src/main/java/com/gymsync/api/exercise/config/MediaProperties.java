package com.gymsync.api.exercise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gymsync.media")
public class MediaProperties {

  /** Optional YouTube Data API v3 key. When blank, live YouTube search is skipped. */
  private String youtubeApiKey = "";

  public String getYoutubeApiKey() {
    return youtubeApiKey;
  }

  public void setYoutubeApiKey(String youtubeApiKey) {
    this.youtubeApiKey = youtubeApiKey;
  }

  public boolean hasYoutubeApiKey() {
    return youtubeApiKey != null && !youtubeApiKey.isBlank();
  }
}
