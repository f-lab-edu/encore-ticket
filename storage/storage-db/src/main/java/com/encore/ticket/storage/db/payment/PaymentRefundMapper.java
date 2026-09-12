package com.encore.ticket.storage.db.payment;

import com.encore.ticket.core.payment.domain.PaymentRefund;
import com.encore.ticket.core.payment.domain.PaymentRefundRecovery;
import com.encore.ticket.core.payment.domain.PaymentRefundAttention;

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
                entity.failureReason(),
                new PaymentRefundRecovery(entity.recoveryCategory(), entity.recoveryErrorCode(),
                        entity.retryCount(), entity.nextRetryAt(), entity.recoveryStopReason()),
                new PaymentRefundAttention(entity.attentionReason(), entity.attentionSince(),
                        entity.attentionResolvedAt()));
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
                .recoveryCategory(refund.recovery().category())
                .recoveryErrorCode(refund.recovery().errorCode())
                .retryCount(refund.recovery().retryCount())
                .nextRetryAt(refund.recovery().nextRetryAt())
                .recoveryStopReason(refund.recovery().stopReason())
                .attentionReason(refund.attention().reason())
                .attentionSince(refund.attention().since())
                .attentionResolvedAt(refund.attention().resolvedAt())
                .build();
    }
}
