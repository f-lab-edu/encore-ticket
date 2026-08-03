package com.encore.ticket.payment.internal;

import com.encore.ticket.payment.api.dto.PaymentStatus;

import java.time.OffsetDateTime;

class Payment {

    private final String paymentKey;
    private final String orderId;
    private final Long amount;
    private final Long reservationId;

    private PaymentStatus status;
    private String method;
    private OffsetDateTime approvedAt;

    Payment(String paymentKey, String orderId, Long amount, Long reservationId,
            PaymentStatus status, String method, OffsetDateTime approvedAt) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.amount = amount;
        this.reservationId = reservationId;
        this.status = status;
        this.method = method;
        this.approvedAt = approvedAt;
    }

    static Payment accept(String paymentKey, String orderId, Long amount, Long reservationId) {
        return new Payment(paymentKey, orderId, amount, reservationId, PaymentStatus.PENDING, null, null);
    }

    boolean sameRequestAs(String orderId, Long amount) {
        return this.orderId.equals(orderId) && this.amount.equals(amount);
    }

    boolean boundToOtherKey(String paymentKey) {
        return !this.paymentKey.equals(paymentKey);
    }

    String paymentKey() {
        return paymentKey;
    }

    String orderId() {
        return orderId;
    }

    Long amount() {
        return amount;
    }

    Long reservationId() {
        return reservationId;
    }

    PaymentStatus status() {
        return status;
    }

    String method() {
        return method;
    }

    OffsetDateTime approvedAt() {
        return approvedAt;
    }
}
