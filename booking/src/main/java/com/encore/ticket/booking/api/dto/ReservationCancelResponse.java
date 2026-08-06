package com.encore.ticket.booking.api.dto;

import java.time.OffsetDateTime;

public record ReservationCancelResponse(
        long id,
        ReservationStatus status,
        OffsetDateTime cancelledAt) {
}
