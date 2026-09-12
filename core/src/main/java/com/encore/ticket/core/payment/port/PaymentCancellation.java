package com.encore.ticket.core.payment.port;

import java.time.OffsetDateTime;

public record PaymentCancellation(
        State state,
        String paymentKey,
        Long canceledAmount,
        OffsetDateTime canceledAt,
        String failureCode,
        String failureMessage) {

    public enum State {
        COMPLETED,
        FAILED
    }

    public static PaymentCancellation completed(
            String paymentKey, Long canceledAmount, OffsetDateTime canceledAt) {
        return new PaymentCancellation(
                State.COMPLETED, paymentKey, canceledAmount, canceledAt, null, null);
    }

    public static PaymentCancellation failed(
            String paymentKey, String failureCode, String failureMessage) {
        return new PaymentCancellation(
                State.FAILED, paymentKey, null, null, failureCode, failureMessage);
    }

    public boolean isCompleted() {
        return state == State.COMPLETED;
    }
}
