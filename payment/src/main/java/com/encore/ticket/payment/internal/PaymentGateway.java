package com.encore.ticket.payment.internal;

interface PaymentGateway {

    void requestApproval(String paymentKey, String orderId, Long amount);
}
