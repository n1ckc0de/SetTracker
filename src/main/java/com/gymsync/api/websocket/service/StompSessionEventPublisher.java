package com.gymsync.api.websocket.service;

import com.gymsync.api.websocket.dto.SessionEvent;
import com.gymsync.api.websocket.dto.SessionEventType;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Primary
public class StompSessionEventPublisher implements SessionEventPublisher {

  private final SimpMessagingTemplate messagingTemplate;

  public StompSessionEventPublisher(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  @Override
  public void publishSessionStarted(UUID workoutId, UUID userId) {
    publish(
        SessionEventType.SESSION_STARTED, workoutId, userId, Map.of("userId", userId.toString()));
  }

  @Override
  public void publishSessionCompleted(UUID workoutId, UUID userId) {
    publish(
        SessionEventType.SESSION_COMPLETED, workoutId, userId, Map.of("userId", userId.toString()));
  }

  @Override
  public void publishSetLogged(UUID workoutId, UUID setId) {
    publish(SessionEventType.SET_LOGGED, workoutId, null, Map.of("setId", setId.toString()));
  }

  @Override
  public void publishSetUpdated(UUID workoutId, UUID setId) {
    publish(SessionEventType.SET_UPDATED, workoutId, null, Map.of("setId", setId.toString()));
  }

  @Override
  public void publishSetDeleted(UUID workoutId, UUID setId) {
    publish(SessionEventType.SET_DELETED, workoutId, null, Map.of("setId", setId.toString()));
  }

  private void publish(
      SessionEventType type, UUID workoutId, UUID userId, Map<String, Object> data) {
    SessionEvent event = SessionEvent.of(type, workoutId, data);
    messagingTemplate.convertAndSend(sessionDestination(workoutId), event);
    if (userId != null) {
      messagingTemplate.convertAndSend(userDestination(userId), event);
    }
  }

  public static String sessionDestination(UUID workoutId) {
    return "/topic/session/" + workoutId;
  }

  public static String userDestination(UUID userId) {
    return "/topic/user/" + userId + "/sessions";
  }
}
