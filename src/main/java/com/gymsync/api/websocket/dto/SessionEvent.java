package com.gymsync.api.websocket.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SessionEvent(
    UUID eventId,
    SessionEventType eventType,
    Instant occurredAt,
    UUID workoutId,
    Map<String, Object> data) {

  public static SessionEvent of(SessionEventType type, UUID workoutId, Map<String, Object> data) {
    return new SessionEvent(UUID.randomUUID(), type, Instant.now(), workoutId, data);
  }
}
