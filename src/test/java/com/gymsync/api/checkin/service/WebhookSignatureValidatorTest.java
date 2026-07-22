package com.gymsync.api.checkin.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymsync.api.checkin.config.WebhookProperties;
import com.gymsync.api.common.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookSignatureValidatorTest {

  private static final String SECRET = "test-webhook-secret";

  private WebhookSignatureValidator validator;
  private byte[] body;

  @BeforeEach
  void setUp() {
    WebhookProperties properties = new WebhookProperties();
    properties.setSecret(SECRET);
    properties.setTimestampToleranceSeconds(300);
    validator = new WebhookSignatureValidator(properties);
    body = "{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);
  }

  private String currentTimestamp() {
    return String.valueOf(Instant.now().getEpochSecond());
  }

  @Test
  void validate_correctSignature_doesNotThrow() {
    String timestamp = currentTimestamp();
    String signature = validator.sign(timestamp, body);

    assertThatCode(() -> validator.validate(timestamp, signature, body)).doesNotThrowAnyException();
  }

  @Test
  void validate_signatureWithSha256Prefix_isAccepted() {
    String timestamp = currentTimestamp();
    String signature = "sha256=" + validator.sign(timestamp, body);

    assertThatCode(() -> validator.validate(timestamp, signature, body)).doesNotThrowAnyException();
  }

  @Test
  void validate_invalidSignature_throwsUnauthorized() {
    String timestamp = currentTimestamp();

    assertThatThrownBy(() -> validator.validate(timestamp, "0".repeat(64), body))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("signature");
  }

  @Test
  void validate_signatureComputedOverDifferentBody_isRejected() {
    String timestamp = currentTimestamp();
    String signature = validator.sign(timestamp, body);
    byte[] tamperedBody = "{\"eventId\":\"evt-2\"}".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> validator.validate(timestamp, signature, tamperedBody))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void validate_missingSignature_throwsUnauthorized() {
    String timestamp = currentTimestamp();

    assertThatThrownBy(() -> validator.validate(timestamp, null, body))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("signature");
  }

  @Test
  void validate_missingTimestamp_throwsUnauthorized() {
    assertThatThrownBy(() -> validator.validate(null, "irrelevant", body))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("timestamp");
  }

  @Test
  void validate_nonNumericTimestamp_throwsUnauthorized() {
    assertThatThrownBy(() -> validator.validate("not-a-number", "irrelevant", body))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Invalid webhook timestamp");
  }

  @Test
  void validate_expiredTimestamp_throwsUnauthorized() {
    String timestamp = String.valueOf(Instant.now().minusSeconds(600).getEpochSecond());
    String signature = validator.sign(timestamp, body);

    assertThatThrownBy(() -> validator.validate(timestamp, signature, body))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("tolerance");
  }

  @Test
  void validate_futureTimestampBeyondTolerance_throwsUnauthorized() {
    String timestamp = String.valueOf(Instant.now().plusSeconds(600).getEpochSecond());
    String signature = validator.sign(timestamp, body);

    assertThatThrownBy(() -> validator.validate(timestamp, signature, body))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("tolerance");
  }
}
