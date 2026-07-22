package com.gymsync.api.checkin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gymsync.webhook")
public class WebhookProperties {

  private String secret = "change-me-webhook-secret";
  private long timestampToleranceSeconds = 300;

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public long getTimestampToleranceSeconds() {
    return timestampToleranceSeconds;
  }

  public void setTimestampToleranceSeconds(long timestampToleranceSeconds) {
    this.timestampToleranceSeconds = timestampToleranceSeconds;
  }
}
