package com.gymsync.api.websocket.service;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Fallback publisher used only when no STOMP-backed publisher is available (e.g. narrow unit
 * tests).
 */
@Service
@ConditionalOnMissingBean(StompSessionEventPublisher.class)
public class NoOpSessionEventPublisher implements SessionEventPublisher {

  @Override
  public void publishSessionStarted(UUID workoutId, UUID userId) {}

  @Override
  public void publishSessionCompleted(UUID workoutId, UUID userId) {}

  @Override
  public void publishSetLogged(UUID workoutId, UUID setId) {}

  @Override
  public void publishSetUpdated(UUID workoutId, UUID setId) {}

  @Override
  public void publishSetDeleted(UUID workoutId, UUID setId) {}
}
