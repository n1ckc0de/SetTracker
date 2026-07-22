package com.gymsync.api.common.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code gymsync.cors.*} configuration. Allowed origins must be an explicit list (never a
 * wildcard) because the API allows credentials in CORS responses.
 */
@ConfigurationProperties("gymsync.cors")
public class CorsProperties {

  private List<String> allowedOrigins = new ArrayList<>();

  public List<String> getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }
}
