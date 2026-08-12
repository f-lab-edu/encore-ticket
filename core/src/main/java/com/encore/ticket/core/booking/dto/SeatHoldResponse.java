package com.encore.ticket.core.booking.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SeatHoldResponse(
        String holdId,
        long scheduleId,
        List<Long> seatIds,
        long totalAmount,
        OffsetDateTime expiresAt) {
}
