package com.encore.ticket.core.booking.dto;

import java.time.OffsetDateTime;

public record ReservationCancelResponse(
        long id,
        ReservationStatus status,
        OffsetDateTime cancelledAt) {
}
