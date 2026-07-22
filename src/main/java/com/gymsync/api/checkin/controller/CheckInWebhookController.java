package com.gymsync.api.checkin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsync.api.checkin.dto.CheckInWebhookRequest;
import com.gymsync.api.checkin.dto.CheckInWebhookResponse;
import com.gymsync.api.checkin.service.CheckInService;
import com.gymsync.api.checkin.service.WebhookSignatureValidator;
import com.gymsync.api.checkin.web.CachedBodyHttpServletRequest;
import com.gymsync.api.common.exception.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Webhooks")
public class CheckInWebhookController {

  public static final String TIMESTAMP_HEADER = "X-Webhook-Timestamp";
  public static final String SIGNATURE_HEADER = "X-Webhook-Signature";

  private final WebhookSignatureValidator signatureValidator;
  private final CheckInService checkInService;
  private final ObjectMapper objectMapper;

  public CheckInWebhookController(
      WebhookSignatureValidator signatureValidator,
      CheckInService checkInService,
      ObjectMapper objectMapper) {
    this.signatureValidator = signatureValidator;
    this.checkInService = checkInService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/checkin")
  @Operation(summary = "Gym check-in webhook (HMAC signed)")
  public ResponseEntity<CheckInWebhookResponse> checkIn(
      @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
      @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
      HttpServletRequest request)
      throws Exception {
    // Spring Security's FilterChainProxy re-wraps the request (e.g. in a FirewalledRequest)
    // before it reaches this controller, so a plain `instanceof` check on `request` itself
    // would fail even though WebhookBodyCachingFilter did run. WebUtils.getNativeRequest
    // unwraps any chain of ServletRequestWrapper to find our cached-body wrapper.
    CachedBodyHttpServletRequest cached =
        WebUtils.getNativeRequest(request, CachedBodyHttpServletRequest.class);
    if (cached == null) {
      throw new BadRequestException("Webhook body could not be read for signature validation.");
    }
    byte[] rawBody = cached.getCachedBody();
    signatureValidator.validate(timestamp, signature, rawBody);

    CheckInWebhookRequest payload = objectMapper.readValue(rawBody, CheckInWebhookRequest.class);
    // Manual validation since we bypass @RequestBody/@Valid for raw signature verification
    if (payload.eventId() == null || payload.eventId().isBlank()) {
      throw new BadRequestException("eventId is required.");
    }
    if (payload.userId() == null) {
      throw new BadRequestException("userId is required.");
    }
    if (payload.source() == null || payload.source().isBlank()) {
      throw new BadRequestException("source is required.");
    }

    CheckInWebhookResponse response = checkInService.process(payload);
    HttpStatus status =
        response.status() == com.gymsync.api.checkin.domain.CheckInStatus.DUPLICATE
            ? HttpStatus.OK
            : HttpStatus.CREATED;
    return ResponseEntity.status(status).body(response);
  }
}
