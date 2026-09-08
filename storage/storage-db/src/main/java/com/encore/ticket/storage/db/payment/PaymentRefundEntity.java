package com.encore.ticket.storage.db.payment;

import com.encore.ticket.core.payment.dto.PaymentRefundStatus;
import com.encore.ticket.core.payment.domain.PaymentRefundRecovery;
import com.encore.ticket.core.payment.domain.PaymentRefundAttention;
import com.encore.ticket.core.payment.dto.RefundRecoveryCategory;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "payment_refund")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentRefundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long paymentId;
    private String paymentKey;
    private String idempotencyKey;
    private Long amount;

    @Enumerated(EnumType.STRING)
    private PaymentRefundStatus status;

    private String reason;
    private OffsetDateTime completedAt;
    private String failureReason;

    @Enumerated(EnumType.STRING)
    private RefundRecoveryCategory recoveryCategory;
    private String recoveryErrorCode;
    private int retryCount;
    private OffsetDateTime nextRetryAt;
    private String recoveryStopReason;

    @Enumerated(EnumType.STRING)
    private PaymentRefundAttention.Reason attentionReason;
    private OffsetDateTime attentionSince;
    private OffsetDateTime attentionResolvedAt;

    private String executionToken;
    private OffsetDateTime executionUntil;
    private OffsetDateTime requestStartedAt;
    private OffsetDateTime lastRecoveryAt;

    boolean refreshAttention(OffsetDateTime now) {
        PaymentRefundAttention.Reason next = null;
        if (status != PaymentRefundStatus.COMPLETED) {
            if (retryCount >= 5) {
                next = PaymentRefundAttention.Reason.RETRY_LIMIT_REACHED;
            } else if (recoveryCategory == RefundRecoveryCategory.CORRECTION_OR_REVIEW_REQUIRED) {
                next = PaymentRefundAttention.Reason.CORRECTION_OR_REVIEW_REQUIRED;
            } else if (recoveryCategory == RefundRecoveryCategory.AUTOMATIC_RECOVERY_CANDIDATE
                    && recoveryStopReason != null) {
                next = PaymentRefundAttention.Reason.REEXECUTION_REVIEW_REQUIRED;
            } else if (recoveryCategory == RefundRecoveryCategory.RESULT_CONFIRMATION_REQUIRED
                    || status == PaymentRefundStatus.FAILED || recoveryStopReason != null) {
                next = PaymentRefundAttention.Reason.RESULT_CONFIRMATION_REQUIRED;
            }
        }
        if (next == attentionReason) {
            return false;
        }
        if (next == null) {
            attentionResolvedAt = now;
        } else if (attentionReason == null) {
            attentionSince = now;
            attentionResolvedAt = null;
        }
        attentionReason = next;
        return true;
    }

    boolean claim(String token, OffsetDateTime now, OffsetDateTime until) {
        if (status == PaymentRefundStatus.COMPLETED
                || executionToken != null && executionUntil != null && executionUntil.isAfter(now)) {
            return false;
        }
        executionToken = token;
        executionUntil = until;
        return true;
    }

    boolean ownsClaim(String token, OffsetDateTime now) {
        return token.equals(executionToken) && executionUntil != null && executionUntil.isAfter(now);
    }

    boolean markRequestStarted(OffsetDateTime now) {
        if (status != PaymentRefundStatus.PENDING || requestStartedAt != null || retryCount != 0) {
            return false;
        }
        requestStartedAt = now;
        return true;
    }

    void releaseClaim(String token) {
        if (token.equals(executionToken)) {
            executionToken = null;
            executionUntil = null;
        }
    }

    void confirmRefund(OffsetDateTime at) {
        status = PaymentRefundStatus.COMPLETED;
        completedAt = at;
        failureReason = null;
    }

    void updateRecovery(PaymentRefundRecovery recovery) {
        recoveryCategory = recovery.category();
        recoveryErrorCode = recovery.errorCode();
        retryCount = recovery.retryCount();
        nextRetryAt = recovery.nextRetryAt();
        recoveryStopReason = recovery.stopReason();
    }

    void markRecovery(OffsetDateTime at) {
        this.lastRecoveryAt = at;
    }

    void fail(String failure) {
        if (status != PaymentRefundStatus.PENDING) {
            return;
        }
        status = PaymentRefundStatus.FAILED;
        failureReason = failure;
    }
}
