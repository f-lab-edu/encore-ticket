package com.encore.ticket.core.booking.dto;

public record QueueTokenResponse(
        String queueToken,
        long scheduleId,
        QueueStatus status,
        int position,
        int estimatedWaitSeconds,
        int pollAfterSeconds,
        boolean resumed,
        int lapsesRemaining) {
}
