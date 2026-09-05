package com.encore.ticket.core.payment.dto;

import java.time.OffsetDateTime;

public record PaymentResultResponse(
        String paymentKey,
        String orderId,
        PaymentStatus paymentStatus,
        Integer pollAfterSeconds,
        Long reservationId,
        Long amount,
        String method,
        String reservationStatus,
        OffsetDateTime approvedAt,
        String holdId,
        String failReason,
        PaymentRefundStatus refundStatus,
        OffsetDateTime refundedAt,
        String refundFailureReason) {
}
