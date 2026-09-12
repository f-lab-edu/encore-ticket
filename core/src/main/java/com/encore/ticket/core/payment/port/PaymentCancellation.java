package com.encore.ticket.core.payment.port;

import java.time.OffsetDateTime;
import com.encore.ticket.core.payment.dto.RefundRecoveryCategory;

public record PaymentCancellation(
        State state,
        String paymentKey,
        Long canceledAmount,
        OffsetDateTime canceledAt,
        String failureCode,
        String failureMessage,
        RefundRecoveryCategory recoveryCategory) {

    public enum State {
        COMPLETED,
        NOT_CANCELED,
        FAILED
    }

    public static PaymentCancellation completed(
            String paymentKey, Long canceledAmount, OffsetDateTime canceledAt) {
        return new PaymentCancellation(
                State.COMPLETED, paymentKey, canceledAmount, canceledAt, null, null, null);
    }

    public static PaymentCancellation failed(
            String paymentKey, String failureCode, String failureMessage) {
        return failed(paymentKey, failureCode, failureMessage,
                RefundRecoveryCategory.CORRECTION_OR_REVIEW_REQUIRED);
    }

    public static PaymentCancellation failed(String paymentKey, String code, String message,
            RefundRecoveryCategory category) {
        return new PaymentCancellation(State.FAILED, paymentKey, null, null, code, message, category);
    }

    public static PaymentCancellation notCanceled(String paymentKey) {
        return new PaymentCancellation(State.NOT_CANCELED, paymentKey, null, null, null, null, null);
    }

    public boolean isCompleted() {
        return state == State.COMPLETED;
    }
}
