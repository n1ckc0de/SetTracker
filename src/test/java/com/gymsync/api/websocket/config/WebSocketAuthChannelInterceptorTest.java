package com.gymsync.api.websocket.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gymsync.api.auth.security.CustomUserDetailsService;
import com.gymsync.api.auth.security.JwtService;
import com.gymsync.api.auth.security.UserPrincipal;
import com.gymsync.api.user.domain.Role;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.workout.domain.ImportStatus;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.domain.WorkoutSource;
import com.gymsync.api.workout.domain.WorkoutStatus;
import com.gymsync.api.workout.repository.WorkoutRepository;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

class WebSocketAuthChannelInterceptorTest {

  private JwtService jwtService;
  private CustomUserDetailsService userDetailsService;
  private WorkoutRepository workoutRepository;
  private WebSocketAuthChannelInterceptor interceptor;

  private UUID userId;
  private UserPrincipal principal;

  @BeforeEach
  void setUp() {
    jwtService = mock(JwtService.class);
    userDetailsService = mock(CustomUserDetailsService.class);
    workoutRepository = mock(WorkoutRepository.class);
    interceptor =
        new WebSocketAuthChannelInterceptor(jwtService, userDetailsService, workoutRepository);

    userId = UUID.randomUUID();
    User user = new User("ws@example.com", "hash", "WS", Role.USER);
    ReflectionTestUtils.setField(user, "id", userId);
    principal = UserPrincipal.fromUser(user);
  }

  @Test
  void connect_withoutToken_rejects() {
    assertThatThrownBy(() -> interceptor.preSend(connectMessage(null), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing");
  }

  @Test
  void connect_withValidBearerToken_succeeds() {
    Claims claims = mock(Claims.class);
    when(jwtService.parseClaims("good.jwt.token")).thenReturn(claims);
    when(jwtService.extractUserId(claims)).thenReturn(userId);
    when(userDetailsService.loadUserById(userId)).thenReturn(principal);

    assertThatCode(() -> interceptor.preSend(connectMessage("Bearer good.jwt.token"), null))
        .doesNotThrowAnyException();
  }

  @Test
  void subscribe_toAnotherUsersSessionTopic_rejects() {
    UUID otherUserId = UUID.randomUUID();
    UUID workoutId = UUID.randomUUID();
    User other = new User("other@example.com", "hash", "Other", Role.USER);
    ReflectionTestUtils.setField(other, "id", otherUserId);
    Workout workout =
        new Workout(
            other,
            Instant.now(),
            WorkoutStatus.ACTIVE,
            WorkoutSource.MANUAL,
            ImportStatus.NOT_APPLICABLE);
    ReflectionTestUtils.setField(workout, "id", workoutId);
    when(workoutRepository.findById(workoutId)).thenReturn(Optional.of(workout));

    assertThatThrownBy(
            () ->
                interceptor.preSend(
                    subscribeMessage("/topic/session/" + workoutId, principal), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Not allowed");
  }

  @Test
  void subscribe_toOwnUserTopic_allows() {
    assertThatCode(
            () ->
                interceptor.preSend(
                    subscribeMessage("/topic/user/" + userId + "/sessions", principal), null))
        .doesNotThrowAnyException();
  }

  @Test
  void subscribe_toAnotherUsersTopic_rejects() {
    assertThatThrownBy(
            () ->
                interceptor.preSend(
                    subscribeMessage("/topic/user/" + UUID.randomUUID() + "/sessions", principal),
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("another user's");
  }

  private static Message<byte[]> connectMessage(String authorizationHeader) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.setLeaveMutable(true);
    if (authorizationHeader != null) {
      accessor.setNativeHeader("Authorization", authorizationHeader);
    }
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  private static Message<byte[]> subscribeMessage(String destination, UserPrincipal principal) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setLeaveMutable(true);
    accessor.setDestination(destination);
    accessor.setUser(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }
}
