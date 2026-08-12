package com.encore.ticket.core.payment.dto;

import java.time.OffsetDateTime;

public record PaymentConfirmResponse(
        String paymentKey,
        String orderId,
        PaymentStatus paymentStatus,
        Long reservationId,
        Long amount,
        String method,
        String reservationStatus,
        OffsetDateTime approvedAt) {
}
