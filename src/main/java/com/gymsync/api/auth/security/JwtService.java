package com.gymsync.api.auth.security;

import com.gymsync.api.user.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Creates and validates stateless HS256 JWTs. The subject is the user's UUID id; {@code email} and
 * {@code role} are carried as claims so callers can avoid extra lookups for display purposes
 * (authorization still re-checks the database via {@link CustomUserDetailsService} on every
 * request).
 */
@Service
public class JwtService {

  private static final String CLAIM_EMAIL = "email";
  private static final String CLAIM_ROLE = "role";

  private final JwtProperties properties;
  private final SecretKey key;

  public JwtService(JwtProperties properties) {
    this.properties = properties;
    this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
  }

  public String createToken(UUID userId, String email, Role role) {
    Instant now = Instant.now();
    Instant expiry = now.plusMillis(properties.getExpirationMs());
    return Jwts.builder()
        .subject(userId.toString())
        .claim(CLAIM_EMAIL, email)
        .claim(CLAIM_ROLE, role.name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  public long getExpirationSeconds() {
    return properties.getExpirationMs() / 1000;
  }

  /**
   * Parses and validates the token's signature and expiration.
   *
   * @throws JwtException if the token is malformed, expired, or has an invalid signature
   */
  public Claims parseClaims(String token) throws JwtException {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  public UUID extractUserId(Claims claims) {
    return UUID.fromString(claims.getSubject());
  }
}
