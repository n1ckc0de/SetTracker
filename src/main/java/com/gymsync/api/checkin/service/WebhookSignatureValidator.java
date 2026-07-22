package com.gymsync.api.checkin.service;

import com.gymsync.api.checkin.config.WebhookProperties;
import com.gymsync.api.common.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Validates HMAC-SHA256 webhook signatures.
 *
 * <p>Signed payload format: {@code timestamp + "." + rawBody} (UTF-8). Header signature may be
 * hex-encoded digests, optionally prefixed with {@code sha256=}.
 */
@Component
public class WebhookSignatureValidator {

  private final WebhookProperties properties;

  public WebhookSignatureValidator(WebhookProperties properties) {
    this.properties = properties;
  }

  public void validate(String timestampHeader, String signatureHeader, byte[] rawBody) {
    if (timestampHeader == null || timestampHeader.isBlank()) {
      throw new UnauthorizedException("Missing webhook timestamp.");
    }
    if (signatureHeader == null || signatureHeader.isBlank()) {
      throw new UnauthorizedException("Missing webhook signature.");
    }
    long timestampEpoch;
    try {
      timestampEpoch = Long.parseLong(timestampHeader.trim());
    } catch (NumberFormatException ex) {
      throw new UnauthorizedException("Invalid webhook timestamp.");
    }
    long now = Instant.now().getEpochSecond();
    if (Math.abs(now - timestampEpoch) > properties.getTimestampToleranceSeconds()) {
      throw new UnauthorizedException("Webhook timestamp is outside the allowed tolerance.");
    }

    String expected = hmacHex(timestampHeader.trim() + ".", rawBody);
    String provided = normalizeSignature(signatureHeader);
    if (!constantTimeEquals(expected, provided)) {
      throw new UnauthorizedException("Invalid webhook signature.");
    }
  }

  public String sign(String timestamp, byte[] rawBody) {
    return hmacHex(timestamp + ".", rawBody);
  }

  private String hmacHex(String prefix, byte[] rawBody) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update(prefix.getBytes(StandardCharsets.UTF_8));
      mac.update(rawBody);
      return toHex(mac.doFinal());
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      throw new IllegalStateException("Unable to compute webhook HMAC", ex);
    }
  }

  private static String normalizeSignature(String signatureHeader) {
    String value = signatureHeader.trim();
    if (value.regionMatches(true, 0, "sha256=", 0, 7)) {
      value = value.substring(7).trim();
    }
    return value.toLowerCase();
  }

  private static boolean constantTimeEquals(String expectedHex, String providedHex) {
    byte[] expected = expectedHex.getBytes(StandardCharsets.UTF_8);
    byte[] provided = providedHex.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(expected, provided);
  }

  private static String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
