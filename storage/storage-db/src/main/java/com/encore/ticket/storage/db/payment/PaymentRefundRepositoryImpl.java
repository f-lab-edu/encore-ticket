package com.encore.ticket.storage.db.payment;

import com.encore.ticket.core.payment.domain.PaymentRefund;
import com.encore.ticket.core.payment.domain.PaymentRefundRecovery;
import com.encore.ticket.core.payment.dto.PaymentRefundStatus;
import com.encore.ticket.core.payment.port.PaymentRefundRepository;
import com.encore.ticket.core.payment.port.PaymentRefundClaim;
import com.encore.ticket.core.payment.port.PaymentCancellation;
import com.encore.ticket.core.payment.dto.RefundRecoveryCategory;
import java.util.UUID;

import java.time.OffsetDateTime;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Slf4j
@RequiredArgsConstructor
public class PaymentRefundRepositoryImpl implements PaymentRefundRepository {

    private final PaymentRefundJpaRepository jpa;
    private final Clock clock;

    @Override
    @Transactional
    public Optional<PaymentRefundClaim> tryClaim(Long paymentId) {
        PaymentRefundEntity entity = jpa.findByPaymentIdForUpdate(paymentId).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now(clock);
        String token = UUID.randomUUID().toString();
        // 재시도 간격과 별개인 실행권. 만료 뒤에는 이전 실행자의 저장을 차단한다.
        if (!entity.claim(token, now, now.plusMinutes(1))) {
            return Optional.empty();
        }
        return Optional.of(new PaymentRefundClaim(token, PaymentRefundMapper.toDomain(entity),
                entity.requestStartedAt()));
    }

    @Override
    @Transactional
    public boolean markRequestStarted(PaymentRefundClaim claim) {
        PaymentRefundEntity entity = lock(claim.refund());
        return entity.ownsClaim(claim.token(), OffsetDateTime.now(clock))
                && entity.markRequestStarted(OffsetDateTime.now(clock));
    }

    @Override
    @Transactional
    public PaymentRefund finishClaim(PaymentRefundClaim claim, PaymentCancellation cancellation,
                                     PaymentRefundRecovery recovery) {
        PaymentRefundEntity entity = lock(claim.refund());
        if (!entity.ownsClaim(claim.token(), OffsetDateTime.now(clock))
                || entity.status() == PaymentRefundStatus.COMPLETED) {
            return PaymentRefundMapper.toDomain(entity);
        }
        if (cancellation != null && cancellation.isCompleted()) {
            if (!entity.paymentKey().equals(cancellation.paymentKey())
                    || !entity.amount().equals(cancellation.canceledAmount())
                    || cancellation.canceledAt() == null) {
                throw new IllegalArgumentException("환불 완료 결과가 기존 환불과 일치하지 않습니다");
            }
            entity.confirmRefund(cancellation.canceledAt());
            entity.updateRecovery(new PaymentRefundRecovery(null, null,
                    entity.retryCount(), null, null));
        } else if (recovery != null) {
            entity.updateRecovery(recovery);
            if (recovery.category() == RefundRecoveryCategory.CORRECTION_OR_REVIEW_REQUIRED) {
                entity.fail(recovery.errorCode());
            }
        }
        recordAttention(entity);
        entity.releaseClaim(claim.token());
        return PaymentRefundMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void releaseClaim(PaymentRefundClaim claim) {
        lock(claim.refund()).releaseClaim(claim.token());
    }

    @Override
    public Optional<PaymentRefund> findByPaymentId(Long id) {
        return jpa.findByPaymentId(id).map(PaymentRefundMapper::toDomain);
    }

    @Override
    @Transactional
    public PaymentRefund updateRecovery(PaymentRefund expected, PaymentRefundRecovery recovery) {
        Objects.requireNonNull(recovery, "복구 정보가 필요합니다");
        PaymentRefundEntity entity = lock(expected);
        PaymentRefund current = PaymentRefundMapper.toDomain(entity);
        if (current.status() == PaymentRefundStatus.COMPLETED
                || current.status() != expected.status()
                || !current.recovery().equals(expected.recovery())) {
            return current;
        }
        entity.updateRecovery(recovery);
        recordAttention(entity);
        return PaymentRefundMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public PaymentRefund fail(PaymentRefund refund, String reason) {
        PaymentRefundEntity entity = lock(refund);
        entity.fail(reason);
        recordAttention(entity);
        return PaymentRefundMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public List<PaymentRefund> findForResultRecovery(OffsetDateTime before, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("환불 복구 batch 크기는 1 이상이어야 합니다: " + batchSize);
        }
        List<PaymentRefundEntity> selected = jpa.findForResultRecovery(before, OffsetDateTime.now(clock), batchSize);
        OffsetDateTime claimedAt = OffsetDateTime.now(clock);
        selected.forEach(refund -> refund.markRecovery(claimedAt));
        jpa.flush();
        return selected.stream().map(PaymentRefundMapper::toDomain).toList();
    }

    private void recordAttention(PaymentRefundEntity entity) {
        if (!entity.refreshAttention(OffsetDateTime.now(clock))) {
            return;
        }
        Long refundId = entity.id();
        Long paymentId = entity.paymentId();
        var reason = entity.attentionReason();
        int retryCount = entity.retryCount();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (reason == null) {
                    log.info("event=refund_attention_resolved refundId={} paymentId={} retryCount={}",
                            refundId, paymentId, retryCount);
                } else {
                    log.warn("event=refund_attention_required refundId={} paymentId={} reason={} retryCount={}",
                            refundId, paymentId, reason, retryCount);
                }
            }
        });
    }

    private PaymentRefundEntity lock(PaymentRefund refund) {
        return jpa.findByIdempotencyKeyForUpdate(refund.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException(
                        "존재하지 않는 환불입니다: " + refund.idempotencyKey()));
    }
}
