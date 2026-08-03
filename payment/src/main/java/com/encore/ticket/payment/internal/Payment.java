package com.encore.ticket.payment.internal;

import com.encore.ticket.payment.api.ReservationCharge;
import com.encore.ticket.payment.api.dto.PaymentStatus;

import java.time.OffsetDateTime;

class Payment {

    private final String paymentKey;
    private final String orderId;
    private final Long amount;
    private final Long reservationId;
    private final Long memberId;
    private final String holdId;

    private PaymentStatus status;
    private String method;
    private OffsetDateTime approvedAt;
    private String failReason;

    Payment(String paymentKey, String orderId, Long amount, Long reservationId, Long memberId, String holdId,
            PaymentStatus status, String method, OffsetDateTime approvedAt, String failReason) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.amount = amount;
        this.reservationId = reservationId;
        this.memberId = memberId;
        this.holdId = holdId;
        this.status = status;
        this.method = method;
        this.approvedAt = approvedAt;
        this.failReason = failReason;
    }

    static Payment accept(String paymentKey, String orderId, Long amount, ReservationCharge charge) {
        return new Payment(paymentKey, orderId, amount, charge.reservationId(), charge.memberId(), charge.holdId(),
                PaymentStatus.PENDING, null, null, null);
    }

    boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    boolean isFailed() {
        return status == PaymentStatus.FAILED;
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

    String holdId() {
        return holdId;
    }

    String failReason() {
        return failReason;
    }
}
