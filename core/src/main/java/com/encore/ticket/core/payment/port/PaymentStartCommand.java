package com.encore.ticket.core.payment.port;

public record PaymentStartCommand(
        String paymentKey,
        String orderId,
        Long amount,
        Long memberId) {
}
