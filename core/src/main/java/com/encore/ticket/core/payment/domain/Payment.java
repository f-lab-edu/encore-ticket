package com.encore.ticket.core.payment.domain;

import com.encore.ticket.core.payment.dto.PaymentStatus;

import java.time.OffsetDateTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Payment {

    private final Long id;

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

    public static Payment accept(String paymentKey, String orderId, Long amount, ReservationCharge charge) {
        return builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .amount(amount)
                .reservationId(charge.reservationId())
                .memberId(charge.memberId())
                .holdId(charge.holdId())
                .status(PaymentStatus.PENDING)
                .build();
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
}
