package com.gymsync.api.notesimport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gymsync.llm")
public class LlmProperties {

  private String provider = "rule-based";
  private String apiKey = "";
  private String apiUrl = "https://api.openai.com/v1/chat/completions";
  private String model = "gpt-4o-mini";
  private long timeoutMs = 15000;
  private int maxRetries = 1;
  private int maxRawTextLength = 10000;
  private int maxExercises = 20;
  private int maxSetsPerExercise = 20;

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getApiUrl() {
    return apiUrl;
  }

  public void setApiUrl(String apiUrl) {
    this.apiUrl = apiUrl;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public long getTimeoutMs() {
    return timeoutMs;
  }

  public void setTimeoutMs(long timeoutMs) {
    this.timeoutMs = timeoutMs;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public int getMaxRawTextLength() {
    return maxRawTextLength;
  }

  public void setMaxRawTextLength(int maxRawTextLength) {
    this.maxRawTextLength = maxRawTextLength;
  }

  public int getMaxExercises() {
    return maxExercises;
  }

  public void setMaxExercises(int maxExercises) {
    this.maxExercises = maxExercises;
  }

  public int getMaxSetsPerExercise() {
    return maxSetsPerExercise;
  }

  public void setMaxSetsPerExercise(int maxSetsPerExercise) {
    this.maxSetsPerExercise = maxSetsPerExercise;
  }
}
