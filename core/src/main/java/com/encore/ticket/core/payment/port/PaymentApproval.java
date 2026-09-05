package com.encore.ticket.core.payment.port;

import java.time.OffsetDateTime;

public record PaymentApproval(
        State state,
        String paymentKey,
        String orderId,
        Long amount,
        String method,
        OffsetDateTime approvedAt,
        String failureCode,
        String failureMessage) {

    public enum State {
        APPROVED,
        DECLINED,
        PENDING,
        CANCELED
    }

    public static PaymentApproval approved(String paymentKey, String orderId, Long amount,
                                           String method, OffsetDateTime approvedAt) {
        return new PaymentApproval(State.APPROVED, paymentKey, orderId, amount, method,
                approvedAt, null, null);
    }

    public static PaymentApproval declined(String paymentKey, String orderId, Long amount,
                                           String code, String message) {
        return new PaymentApproval(State.DECLINED, paymentKey, orderId, amount, null,
                null, code, message);
    }

    public static PaymentApproval pending(String paymentKey, String orderId, Long amount,
                                          String providerState) {
        return new PaymentApproval(State.PENDING, paymentKey, orderId, amount, null,
                null, providerState, providerState);
    }

    public static PaymentApproval canceled(String paymentKey, String orderId, Long amount,
                                           String providerState) {
        return new PaymentApproval(State.CANCELED, paymentKey, orderId, amount, null,
                null, providerState, providerState);
    }

    public boolean isApproved() {
        return state == State.APPROVED;
    }
}
