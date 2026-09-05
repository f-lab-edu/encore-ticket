package com.encore.ticket.storage.db.payment;

import com.encore.ticket.core.payment.dto.PaymentStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String paymentKey;

    private String orderId;

    private Long amount;
    private Long reservationId;
    private Long memberId;

    private String holdId;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String method;

    private OffsetDateTime approvedAt;

    private String failReason;

    private OffsetDateTime lastRecoveryAt;

    void markRecovery(OffsetDateTime at) {
        this.lastRecoveryAt = at;
    }

    void complete(String method, OffsetDateTime approvedAt) {
        this.status = PaymentStatus.COMPLETED;
        this.method = method;
        this.approvedAt = approvedAt;
        this.failReason = null;
    }

    void fail(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failReason = reason;
    }
}
