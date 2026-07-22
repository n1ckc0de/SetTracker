package com.gymsync.api.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsync.api.common.config.CorsProperties;
import com.gymsync.api.common.exception.ErrorCode;
import com.gymsync.api.common.filter.CorrelationIdFilter;
import com.gymsync.api.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT security configuration.
 *
 * <p>Public endpoints: auth (register/login), actuator health, Swagger/OpenAPI, STOMP handshake
 * ({@code /ws/**}), and the check-in webhook path. The webhook is authenticated via HMAC signature
 * (not JWT), so it must bypass the Spring Security auth filter. Every other {@code /api/**} route
 * requires a valid bearer token.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final ObjectMapper objectMapper;

  public SecurityConfig(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  DaoAuthenticationProvider authenticationProvider(
      CustomUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder);
    provider.setUserDetailsService(userDetailsService);
    return provider;
  }

  @Bean
  AuthenticationManager authenticationManager(DaoAuthenticationProvider authenticationProvider) {
    return authenticationProvider::authenticate;
  }

  @Bean
  JwtAuthenticationFilter jwtAuthenticationFilter(
      JwtService jwtService, CustomUserDetailsService userDetailsService) {
    return new JwtAuthenticationFilter(jwtService, userDetailsService);
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
    CorsConfiguration configuration = new CorsConfiguration();
    // Never combine a wildcard origin with allowCredentials(true); origins must be explicit.
    configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "X-Correlation-Id",
            "X-Webhook-Timestamp",
            "X-Webhook-Signature"));
    configuration.setExposedHeaders(List.of("X-Correlation-Id"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> {})
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/v3/api-docs",
                        "/v3/api-docs.yaml",
                        "/v3/api-docs.yml",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api/auth/**",
                        "/api/webhooks/**",
                        "/ws/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(new JsonUnauthorizedEntryPoint(objectMapper))
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) -> {
                          writeErrorResponse(
                              request,
                              response,
                              HttpStatus.FORBIDDEN,
                              "Forbidden",
                              ErrorCode.FORBIDDEN,
                              "You do not have permission to access this resource.");
                        }))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable);

    return http.build();
  }

  static void writeErrorResponse(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String error,
      ErrorCode code,
      String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
    ErrorResponse body =
        ErrorResponse.of(
            status.value(), error, code, message, request.getRequestURI(), null, correlationId);
    response.getWriter().write(new ObjectMapper().writeValueAsString(body));
  }

  private static final class JsonUnauthorizedEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    private JsonUnauthorizedEntryPoint(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException)
        throws IOException {
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
      ErrorResponse body =
          ErrorResponse.of(
              HttpStatus.UNAUTHORIZED.value(),
              "Unauthorized",
              ErrorCode.UNAUTHORIZED,
              "Authentication is required.",
              request.getRequestURI(),
              null,
              correlationId);
      response.getWriter().write(objectMapper.writeValueAsString(body));
    }
  }
}
