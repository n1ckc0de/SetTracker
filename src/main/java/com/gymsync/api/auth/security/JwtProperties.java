package com.gymsync.api.auth.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code gymsync.jwt.*} configuration. The secret and expiration are loaded from environment
 * configuration (see {@code application.yml}) rather than hardcoded, per spec section 6.
 */
@ConfigurationProperties("gymsync.jwt")
@Validated
public class JwtProperties {

  @NotBlank private String secret;

  @Positive private long expirationMs;

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public long getExpirationMs() {
    return expirationMs;
  }

  public void setExpirationMs(long expirationMs) {
    this.expirationMs = expirationMs;
  }
}
