package com.gymsync.api.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymsync.api.user.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private JwtProperties propertiesWithExpiration(long expirationMs) {
    JwtProperties properties = new JwtProperties();
    properties.setSecret("test-secret-key-must-be-at-least-256-bits-long-for-hs256!!");
    properties.setExpirationMs(expirationMs);
    return properties;
  }

  @Test
  void createToken_thenParseClaims_roundTripsUserIdEmailAndRole() {
    JwtService jwtService = new JwtService(propertiesWithExpiration(60_000));
    UUID userId = UUID.randomUUID();

    String token = jwtService.createToken(userId, "user@example.com", Role.USER);
    Claims claims = jwtService.parseClaims(token);

    assertThat(jwtService.extractUserId(claims)).isEqualTo(userId);
    assertThat(claims.get("email")).isEqualTo("user@example.com");
    assertThat(claims.get("role")).isEqualTo("USER");
  }

  @Test
  void parseClaims_expiredToken_throwsJwtException() throws InterruptedException {
    JwtService jwtService = new JwtService(propertiesWithExpiration(1));
    String token = jwtService.createToken(UUID.randomUUID(), "user@example.com", Role.USER);

    Thread.sleep(15);

    assertThatThrownBy(() -> jwtService.parseClaims(token)).isInstanceOf(ExpiredJwtException.class);
  }

  @Test
  void parseClaims_tamperedToken_throwsJwtException() {
    JwtService jwtService = new JwtService(propertiesWithExpiration(60_000));
    String token = jwtService.createToken(UUID.randomUUID(), "user@example.com", Role.USER);
    String tampered = token.substring(0, token.length() - 2) + "xx";

    assertThatThrownBy(() -> jwtService.parseClaims(tampered)).isInstanceOf(JwtException.class);
  }

  @Test
  void getExpirationSeconds_convertsMillisToSeconds() {
    JwtService jwtService = new JwtService(propertiesWithExpiration(120_000));
    assertThat(jwtService.getExpirationSeconds()).isEqualTo(120L);
  }
}
