package com.gymsync.api.websocket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.gymsync.api.websocket.dto.SessionEvent;
import com.gymsync.api.websocket.dto.SessionEventType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** Verifies destination selection and payload shape for each published event type. */
class StompSessionEventPublisherTest {

  private SimpMessagingTemplate messagingTemplate;
  private StompSessionEventPublisher publisher;

  @BeforeEach
  void setUp() {
    messagingTemplate = mock(SimpMessagingTemplate.class);
    publisher = new StompSessionEventPublisher(messagingTemplate);
  }

  @Test
  void publishSessionStarted_sendsToSessionTopicAndUserTopic() {
    UUID workoutId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    publisher.publishSessionStarted(workoutId, userId);

    verify(messagingTemplate)
        .convertAndSend(eq("/topic/session/" + workoutId), any(SessionEvent.class));
    verify(messagingTemplate)
        .convertAndSend(eq("/topic/user/" + userId + "/sessions"), any(SessionEvent.class));
    verifyNoMoreInteractions(messagingTemplate);
  }

  @Test
  void publishSessionCompleted_sendsToSessionTopicAndUserTopicWithCorrectEventType() {
    UUID workoutId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    publisher.publishSessionCompleted(workoutId, userId);

    org.mockito.ArgumentCaptor<SessionEvent> captor =
        org.mockito.ArgumentCaptor.forClass(SessionEvent.class);
    verify(messagingTemplate, times(2)).convertAndSend(any(String.class), captor.capture());
    assertThat(captor.getAllValues())
        .allSatisfy(
            event -> assertThat(event.eventType()).isEqualTo(SessionEventType.SESSION_COMPLETED));
  }

  @Test
  void publishSetLogged_sendsOnlyToSessionTopic_notUserTopic() {
    UUID workoutId = UUID.randomUUID();
    UUID setId = UUID.randomUUID();

    publisher.publishSetLogged(workoutId, setId);

    verify(messagingTemplate)
        .convertAndSend(eq("/topic/session/" + workoutId), any(SessionEvent.class));
    verifyNoMoreInteractions(messagingTemplate);
  }

  @Test
  void publishSetUpdated_payloadContainsSetIdAndWorkoutId() {
    UUID workoutId = UUID.randomUUID();
    UUID setId = UUID.randomUUID();

    publisher.publishSetUpdated(workoutId, setId);

    org.mockito.ArgumentCaptor<SessionEvent> captor =
        org.mockito.ArgumentCaptor.forClass(SessionEvent.class);
    verify(messagingTemplate).convertAndSend(eq("/topic/session/" + workoutId), captor.capture());
    SessionEvent event = captor.getValue();
    assertThat(event.eventType()).isEqualTo(SessionEventType.SET_UPDATED);
    assertThat(event.workoutId()).isEqualTo(workoutId);
    assertThat(event.data()).containsEntry("setId", setId.toString());
    assertThat(event.eventId()).isNotNull();
    assertThat(event.occurredAt()).isNotNull();
  }

  @Test
  void publishSetDeleted_sendsOnlyToSessionTopic() {
    UUID workoutId = UUID.randomUUID();
    UUID setId = UUID.randomUUID();

    publisher.publishSetDeleted(workoutId, setId);

    verify(messagingTemplate)
        .convertAndSend(eq("/topic/session/" + workoutId), any(SessionEvent.class));
    verifyNoMoreInteractions(messagingTemplate);
  }

  @Test
  void sessionDestination_and_userDestination_formatAsExpected() {
    UUID workoutId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    assertThat(StompSessionEventPublisher.sessionDestination(workoutId))
        .isEqualTo("/topic/session/" + workoutId);
    assertThat(StompSessionEventPublisher.userDestination(userId))
        .isEqualTo("/topic/user/" + userId + "/sessions");
  }
}
