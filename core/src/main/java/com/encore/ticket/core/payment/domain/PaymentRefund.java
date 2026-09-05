package com.encore.ticket.core.payment.domain;

import com.encore.ticket.core.payment.dto.PaymentRefundStatus;
import java.time.OffsetDateTime;
import lombok.Builder;

@Builder(toBuilder = true)
public record PaymentRefund(Long id, Long paymentId, String paymentKey, String idempotencyKey,
                            Long amount, PaymentRefundStatus status, String reason,
                            OffsetDateTime completedAt, String failureReason) {

    public static PaymentRefund pending(Payment payment, String reason) {
        if (payment.id() == null) {
            throw new IllegalArgumentException("저장된 결제만 환불할 수 있습니다");
        }
        return new PaymentRefund(null, payment.id(), payment.paymentKey(),
                "refund-" + payment.paymentKey(), payment.amount(),
                PaymentRefundStatus.PENDING, reason, null, null);
    }

    public PaymentRefund complete(OffsetDateTime at) {
        if (status == PaymentRefundStatus.COMPLETED) {
            return this;
        }
        if (status != PaymentRefundStatus.PENDING) {
            throw new IllegalStateException("대기 중인 환불만 완료할 수 있습니다: " + id);
        }
        return toBuilder().status(PaymentRefundStatus.COMPLETED).completedAt(at).failureReason(null).build();
    }

    public PaymentRefund fail(String failure) {
        if (status != PaymentRefundStatus.PENDING) {
            return this;
        }
        return toBuilder().status(PaymentRefundStatus.FAILED).failureReason(failure).build();
    }

    public boolean isPending() {
        return status == PaymentRefundStatus.PENDING;
    }
}
