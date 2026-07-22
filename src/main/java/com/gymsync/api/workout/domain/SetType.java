package com.gymsync.api.workout.domain;

public enum SetType {
  WARMUP,
  WORKING,
  BACKOFF,
  DROP,
  FAILURE,
  AMRAP;

  public boolean countsForPr() {
    return this != WARMUP && this != DROP;
  }
}
