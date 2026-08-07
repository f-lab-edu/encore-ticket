package com.encore.ticket.core.payment.domain;

import java.time.OffsetDateTime;

public record ReservationCharge(
        Long reservationId,
        Long memberId,
        Long amount,
        String currentOrderId,
        String holdId,
        boolean cancelled,
        OffsetDateTime expiresAt) {
}
