package com.encore.ticket.core.payment.domain;

import java.time.OffsetDateTime;

public record PaymentRefundAttention(Reason reason, OffsetDateTime since, OffsetDateTime resolvedAt) {
    public enum Reason {
        CORRECTION_OR_REVIEW_REQUIRED,
        REEXECUTION_REVIEW_REQUIRED,
        RESULT_CONFIRMATION_REQUIRED,
        RETRY_LIMIT_REACHED
    }

    public static PaymentRefundAttention none() {
        return new PaymentRefundAttention(null, null, null);
    }
}
