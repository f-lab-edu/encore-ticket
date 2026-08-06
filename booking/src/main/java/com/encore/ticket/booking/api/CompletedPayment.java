package com.encore.ticket.booking.api;

public record CompletedPayment(String paymentKey, String orderId) {

    public static final CompletedPayment NONE = new CompletedPayment(null, null);
}
