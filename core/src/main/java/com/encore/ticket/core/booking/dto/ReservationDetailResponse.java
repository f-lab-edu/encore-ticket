package com.encore.ticket.core.booking.dto;

import java.time.OffsetDateTime;
import java.util.List;
import com.encore.ticket.core.catalog.domain.Concert;

public record ReservationDetailResponse(
        long id,
        ReservationStatus status,
        Concert concert,
        Schedule schedule,
        List<Seat> seats,
        long totalAmount,
        String paymentKey,
        String orderId,
        OffsetDateTime reservedAt) {

    public record Concert(
            long id,
            String title,
            String posterUrl) {
    }

    public record Schedule(
            long id,
            OffsetDateTime startsAt,
            String venue) {
    }

    public record Seat(
            long id,
            String section,
            String row,
            String number,
            String grade,
            long price) {
    }
}
