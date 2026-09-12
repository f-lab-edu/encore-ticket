package com.encore.ticket.storage.db.payment;

import com.encore.ticket.core.payment.domain.PaymentRefund;

final class PaymentRefundMapper {

    private PaymentRefundMapper() {
    }

    static PaymentRefund toDomain(PaymentRefundEntity entity) {
        return new PaymentRefund(
                entity.id(),
                entity.paymentId(),
                entity.paymentKey(),
                entity.idempotencyKey(),
                entity.amount(),
                entity.status(),
                entity.reason(),
                entity.completedAt(),
                entity.failureReason());
    }

    static PaymentRefundEntity toEntity(PaymentRefund refund) {
        return PaymentRefundEntity.builder()
                .id(refund.id())
                .paymentId(refund.paymentId())
                .paymentKey(refund.paymentKey())
                .idempotencyKey(refund.idempotencyKey())
                .amount(refund.amount())
                .status(refund.status())
                .reason(refund.reason())
                .completedAt(refund.completedAt())
                .failureReason(refund.failureReason())
                .build();
    }
}
