package com.encore.ticket.core.booking;

public record CompletedPayment(String paymentKey, String orderId) {

    public static final CompletedPayment NONE = new CompletedPayment(null, null);
}
