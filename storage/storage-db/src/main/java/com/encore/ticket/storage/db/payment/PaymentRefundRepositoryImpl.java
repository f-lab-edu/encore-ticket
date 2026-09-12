package com.encore.ticket.storage.db.payment;

import com.encore.ticket.core.payment.domain.PaymentRefund;
import com.encore.ticket.core.payment.dto.PaymentRefundStatus;
import com.encore.ticket.core.payment.port.PaymentRefundRepository;

import java.time.OffsetDateTime;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class PaymentRefundRepositoryImpl implements PaymentRefundRepository {

    private final PaymentRefundJpaRepository jpa;
    private final Clock clock;

    @Override
    public Optional<PaymentRefund> findByPaymentId(Long id) {
        return jpa.findByPaymentId(id).map(PaymentRefundMapper::toDomain);
    }

    @Override
    @Transactional
    public PaymentRefund complete(PaymentRefund refund, OffsetDateTime at) {
        PaymentRefundEntity entity = lock(refund);
        entity.complete(at);
        return PaymentRefundMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public PaymentRefund fail(PaymentRefund refund, String reason) {
        PaymentRefundEntity entity = lock(refund);
        entity.fail(reason);
        return PaymentRefundMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public List<PaymentRefund> findPendingForRecovery(OffsetDateTime before, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("환불 복구 batch 크기는 1 이상이어야 합니다: " + batchSize);
        }
        List<PaymentRefundEntity> selected = jpa.findPendingForUpdate(before, batchSize);
        OffsetDateTime claimedAt = OffsetDateTime.now(clock);
        selected.forEach(refund -> refund.markRecovery(claimedAt));
        jpa.flush();
        return selected.stream().map(PaymentRefundMapper::toDomain).toList();
    }

    private PaymentRefundEntity lock(PaymentRefund refund) {
        return jpa.findByIdempotencyKeyForUpdate(refund.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException(
                        "존재하지 않는 환불입니다: " + refund.idempotencyKey()));
    }
}
