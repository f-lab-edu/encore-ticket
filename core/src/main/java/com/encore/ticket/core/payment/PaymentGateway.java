package com.encore.ticket.core.payment;

interface PaymentGateway {

    void requestApproval(String paymentKey, String orderId, Long amount);
}
