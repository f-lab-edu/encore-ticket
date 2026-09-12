package com.encore.ticket.storage.db.booking.reservation;

import com.encore.ticket.core.booking.dto.ReservationStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;

import lombok.*;

@Entity
@Table(name = "reservation")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    private Long memberId;
    private Long scheduleId;

    private String holdId;

    private Long amount;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private OffsetDateTime reservedAt;
    private OffsetDateTime performanceStartsAt;

    private OffsetDateTime originalExpiresAt;

    private OffsetDateTime expiresAt;

    private int paymentAttemptNo;

    private OffsetDateTime paymentStartsAt;

    private OffsetDateTime cancelledAt;

    void changeStatus(ReservationStatus status) {
        this.status = status;
    }

    public void startPayment(OffsetDateTime startedAt) {
        this.paymentStartsAt = startedAt;
    }

    public void confirmPayment() {
        this.status = ReservationStatus.CONFIRMED;
        this.paymentStartsAt = null;
    }

    public void clearPaymentStart() {
        this.paymentStartsAt = null;
    }
}
