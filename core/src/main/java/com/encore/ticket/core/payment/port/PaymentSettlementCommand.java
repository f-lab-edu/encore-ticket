package com.encore.ticket.core.payment.port;

import java.time.OffsetDateTime;

public record PaymentSettlementCommand(
        String paymentKey,
        String orderId,
        Long amount,
        String method,
        OffsetDateTime approvedAt) {
}
