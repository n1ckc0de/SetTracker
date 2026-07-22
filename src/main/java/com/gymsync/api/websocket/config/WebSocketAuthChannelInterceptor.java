package com.gymsync.api.websocket.config;

import com.gymsync.api.auth.security.CustomUserDetailsService;
import com.gymsync.api.auth.security.JwtService;
import com.gymsync.api.auth.security.UserPrincipal;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.repository.WorkoutRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP CONNECT with a Bearer JWT ({@code Authorization} or {@code access_token}
 * native header) and authorizes SUBSCRIBE destinations so users cannot casually listen to others'
 * private workout sessions.
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

  private static final Pattern SESSION_TOPIC =
      Pattern.compile("^/topic/session/([0-9a-fA-F-]{36})$");
  private static final Pattern USER_TOPIC =
      Pattern.compile("^/topic/user/([0-9a-fA-F-]{36})/sessions$");

  private final JwtService jwtService;
  private final CustomUserDetailsService userDetailsService;
  private final WorkoutRepository workoutRepository;

  public WebSocketAuthChannelInterceptor(
      JwtService jwtService,
      CustomUserDetailsService userDetailsService,
      WorkoutRepository workoutRepository) {
    this.jwtService = jwtService;
    this.userDetailsService = userDetailsService;
    this.workoutRepository = workoutRepository;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null) {
      return message;
    }
    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      String token = resolveToken(accessor);
      if (token == null) {
        throw new IllegalArgumentException("Missing WebSocket authentication token");
      }
      try {
        Claims claims = jwtService.parseClaims(token);
        UUID userId = jwtService.extractUserId(claims);
        UserDetails userDetails = userDetailsService.loadUserById(userId);
        Authentication auth =
            new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        accessor.setUser(auth);
      } catch (JwtException | IllegalArgumentException ex) {
        throw new IllegalArgumentException("Invalid WebSocket authentication token");
      }
    } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      Authentication auth = (Authentication) accessor.getUser();
      if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
        throw new IllegalArgumentException("Unauthenticated WebSocket subscription");
      }
      authorizeSubscription(principal, accessor.getDestination());
    }
    return message;
  }

  private String resolveToken(StompHeaderAccessor accessor) {
    String authorization = firstNativeHeader(accessor, "Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      return authorization.substring(7).trim();
    }
    String accessToken = firstNativeHeader(accessor, "access_token");
    if (accessToken != null && !accessToken.isBlank()) {
      return accessToken.trim();
    }
    return null;
  }

  private static String firstNativeHeader(StompHeaderAccessor accessor, String name) {
    List<String> values = accessor.getNativeHeader(name);
    if (values == null || values.isEmpty()) {
      return null;
    }
    return values.getFirst();
  }

  private void authorizeSubscription(UserPrincipal principal, String destination) {
    if (destination == null) {
      throw new IllegalArgumentException("Missing subscription destination");
    }
    Matcher sessionMatcher = SESSION_TOPIC.matcher(destination);
    if (sessionMatcher.matches()) {
      UUID workoutId = UUID.fromString(sessionMatcher.group(1));
      Workout workout =
          workoutRepository
              .findById(workoutId)
              .orElseThrow(() -> new IllegalArgumentException("Workout not found"));
      if (!workout.getUser().getId().equals(principal.getId())) {
        throw new IllegalArgumentException("Not allowed to subscribe to this session");
      }
      return;
    }
    Matcher userMatcher = USER_TOPIC.matcher(destination);
    if (userMatcher.matches()) {
      UUID userId = UUID.fromString(userMatcher.group(1));
      if (!userId.equals(principal.getId())) {
        throw new IllegalArgumentException("Not allowed to subscribe to another user's sessions");
      }
      return;
    }
    throw new IllegalArgumentException("Unsupported subscription destination");
  }
}
