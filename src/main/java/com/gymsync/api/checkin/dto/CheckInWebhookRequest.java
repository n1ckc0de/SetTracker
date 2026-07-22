package com.gymsync.api.checkin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CheckInWebhookRequest(
    @NotBlank String eventId, @NotNull UUID userId, @NotBlank String source, Instant occurredAt) {}
