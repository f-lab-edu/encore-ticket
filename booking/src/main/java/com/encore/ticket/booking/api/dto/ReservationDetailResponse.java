package com.encore.ticket.booking.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

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
