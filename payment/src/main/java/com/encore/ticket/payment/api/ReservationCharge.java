package com.encore.ticket.payment.api;

import java.time.OffsetDateTime;

public record ReservationCharge(
        Long reservationId,
        Long memberId,
        Long amount,
        String currentOrderId,
        boolean cancelled,
        OffsetDateTime expiresAt) {
}
