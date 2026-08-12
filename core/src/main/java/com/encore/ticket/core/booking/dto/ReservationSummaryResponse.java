package com.encore.ticket.core.booking.dto;

import java.time.OffsetDateTime;

public record ReservationSummaryResponse(
        long id,
        String concertTitle,
        String posterUrl,
        OffsetDateTime startsAt,
        String venue,
        int seatCount,
        long totalAmount,
        ReservationStatus status) {
}
