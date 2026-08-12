package com.encore.ticket.core.booking.dto;

import java.time.OffsetDateTime;

public record ReservationCreateResponse(
        long reservationId,
        String orderId,
        String orderName,
        long amount,
        ReservationStatus status,
        OffsetDateTime expiresAt,
        OffsetDateTime originalExpiresAt) {
}
