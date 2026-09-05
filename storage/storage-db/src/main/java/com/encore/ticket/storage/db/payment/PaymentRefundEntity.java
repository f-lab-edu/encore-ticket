package com.encore.ticket.storage.db.payment;

import com.encore.ticket.core.payment.dto.PaymentRefundStatus;
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

    private OffsetDateTime lastRecoveryAt;

    void markRecovery(OffsetDateTime at) {
        this.lastRecoveryAt = at;
    }

    void complete(OffsetDateTime at) {
        if (status == PaymentRefundStatus.COMPLETED) {
            return;
        }
        if (status != PaymentRefundStatus.PENDING) {
            throw new IllegalStateException("대기 중인 환불만 완료할 수 있습니다: " + id);
        }
        status = PaymentRefundStatus.COMPLETED;
        completedAt = at;
        failureReason = null;
    }

    void fail(String failure) {
        if (status != PaymentRefundStatus.PENDING) {
            return;
        }
        status = PaymentRefundStatus.FAILED;
        failureReason = failure;
    }
}
