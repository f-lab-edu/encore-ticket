package com.encore.ticket.storage.db.payment;

import com.encore.ticket.core.payment.domain.Payment;

final class PaymentMapper {

    private PaymentMapper() {
    }

    static Payment toDomain(PaymentEntity entity) {
        return Payment.builder()
                .id(entity.id())
                .paymentKey(entity.paymentKey())
                .orderId(entity.orderId())
                .amount(entity.amount())
                .reservationId(entity.reservationId())
                .memberId(entity.memberId())
                .holdId(entity.holdId())
                .status(entity.status())
                .method(entity.method())
                .approvedAt(entity.approvedAt())
                .failReason(entity.failReason())
                .build();
    }

    static PaymentEntity toEntity(Payment payment) {
        return PaymentEntity.builder()
                .id(payment.id())
                .paymentKey(payment.paymentKey())
                .orderId(payment.orderId())
                .amount(payment.amount())
                .reservationId(payment.reservationId())
                .memberId(payment.memberId())
                .holdId(payment.holdId())
                .status(payment.status())
                .method(payment.method())
                .approvedAt(payment.approvedAt())
                .failReason(payment.failReason())
                .build();
    }
}
