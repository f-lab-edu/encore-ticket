package com.encore.ticket.core.payment.port;

public interface PaymentGateway {

    PaymentApproval approve(String paymentKey, String orderId, Long amount);

    PaymentApproval query(String paymentKey);

    PaymentCancellation cancel(
            String paymentKey, Long amount, String reason, String idempotencyKey);
}
