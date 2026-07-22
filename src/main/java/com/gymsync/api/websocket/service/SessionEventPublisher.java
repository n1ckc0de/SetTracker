package com.gymsync.api.websocket.service;

import java.util.UUID;

/**
 * Abstraction over broadcasting live-session events to WebSocket/STOMP subscribers. Phase 5 will
 * provide a STOMP-backed implementation; for now {@link NoOpSessionEventPublisher} is the only bean
 * so Phases 2-3 can call the publisher from the correct place (after commit) without depending on
 * WebSocket infrastructure yet.
 *
 * <p>Methods intentionally take plain identifiers rather than JPA entities: publishing happens from
 * an {@code afterCommit} transaction-synchronization callback, by which point the persistence
 * context may already be closed, so entities could throw {@code LazyInitializationException} if
 * touched.
 */
public interface SessionEventPublisher {

  void publishSessionStarted(UUID workoutId, UUID userId);

  void publishSessionCompleted(UUID workoutId, UUID userId);

  void publishSetLogged(UUID workoutId, UUID setId);

  void publishSetUpdated(UUID workoutId, UUID setId);

  void publishSetDeleted(UUID workoutId, UUID setId);
}
