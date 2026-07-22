package com.gymsync.api.websocket.config;

import com.gymsync.api.common.config.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final WebSocketAuthChannelInterceptor authChannelInterceptor;
  private final CorsProperties corsProperties;

  public WebSocketConfig(
      WebSocketAuthChannelInterceptor authChannelInterceptor, CorsProperties corsProperties) {
    this.authChannelInterceptor = authChannelInterceptor;
    this.corsProperties = corsProperties;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // Prefer the same explicit origin allow-list used by HTTP CORS. Fall back to localhost
    // only when the list is empty (misconfiguration) so local SockJS clients still work.
    String[] origins =
        corsProperties.getAllowedOrigins().isEmpty()
            ? new String[] {"http://localhost:3000", "http://localhost:8080"}
            : corsProperties.getAllowedOrigins().toArray(String[]::new);
    registry.addEndpoint("/ws/sessions").setAllowedOrigins(origins);
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authChannelInterceptor);
  }
}
