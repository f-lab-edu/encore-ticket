package com.encore.ticket.core.payment.port;

public interface PaymentGateway {

    public void requestApproval(String paymentKey, String orderId, Long amount);
}
