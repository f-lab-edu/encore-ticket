package com.encore.ticket.core.payment.domain;

import com.encore.ticket.core.payment.domain.ReservationCharge;
import com.encore.ticket.core.payment.dto.PaymentStatus;

import java.time.OffsetDateTime;

public class Payment {

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

    public Payment(String paymentKey, String orderId, Long amount, Long reservationId, Long memberId, String holdId,
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

    public static Payment accept(String paymentKey, String orderId, Long amount, ReservationCharge charge) {
        return new Payment(paymentKey, orderId, amount, charge.reservationId(), charge.memberId(), charge.holdId(),
                PaymentStatus.PENDING, null, null, null);
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public boolean isFailed() {
        return status == PaymentStatus.FAILED;
    }

    public boolean sameRequestAs(String orderId, Long amount) {
        return this.orderId.equals(orderId) && this.amount.equals(amount);
    }

    public boolean boundToOtherKey(String paymentKey) {
        return !this.paymentKey.equals(paymentKey);
    }

    public String paymentKey() {
        return paymentKey;
    }

    public String orderId() {
        return orderId;
    }

    public Long amount() {
        return amount;
    }

    public Long reservationId() {
        return reservationId;
    }

    public PaymentStatus status() {
        return status;
    }

    public String method() {
        return method;
    }

    public OffsetDateTime approvedAt() {
        return approvedAt;
    }

    public String holdId() {
        return holdId;
    }

    public String failReason() {
        return failReason;
    }
}
