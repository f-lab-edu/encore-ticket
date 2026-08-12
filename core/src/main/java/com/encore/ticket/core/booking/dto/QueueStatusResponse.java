package com.encore.ticket.core.booking.dto;

import java.time.OffsetDateTime;

public record QueueStatusResponse(
        QueueStatus status,
        int position,
        Integer estimatedWaitSeconds,
        Integer pollAfterSeconds,
        OffsetDateTime admittedUntil) {
}
