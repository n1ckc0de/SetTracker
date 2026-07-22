package com.gymsync.api.checkin.dto;

import com.gymsync.api.checkin.domain.CheckInStatus;
import java.util.UUID;

public record CheckInWebhookResponse(
    UUID checkInId, String eventId, CheckInStatus status, UUID workoutId, String message) {}
