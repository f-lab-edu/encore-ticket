package com.encore.ticket.core.booking.reservation;

import com.encore.ticket.core.booking.dto.ReservationStatus;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

class Reservation {

    private static final String ORDER_ID_PREFIX = "reservation-";
    private static final String ORDER_ID_SEPARATOR = "-";
    private static final int FIRST_PAYMENT_ATTEMPT = 1;
    private static final int PAYMENT_WINDOW_MINUTES = 10;

    private final Long id;
    private final Long memberId;
    private final Long scheduleId;
    private final List<Long> seatIds;
    private final Long amount;
    private final OffsetDateTime originalExpiresAt;
    private final OffsetDateTime performanceStartsAt;
    private final OffsetDateTime reservedAt;

    private ReservationStatus status;
    private OffsetDateTime expiresAt;
    private int paymentAttemptNo;
    private OffsetDateTime paymentStartsAt;
    private OffsetDateTime cancelledAt;

    private Reservation(Builder builder) {
        this.id = builder.id;
        this.memberId = builder.memberId;
        this.scheduleId = builder.scheduleId;
        this.seatIds = builder.seatIds;
        this.amount = builder.amount;
        this.originalExpiresAt = builder.originalExpiresAt;
        this.performanceStartsAt = builder.performanceStartsAt;
        this.reservedAt = builder.reservedAt;
        this.status = builder.status;
        this.expiresAt = builder.expiresAt;
        this.paymentAttemptNo = builder.paymentAttemptNo;
        this.paymentStartsAt = builder.paymentStartsAt;
        this.cancelledAt = builder.cancelledAt;
    }

    static Reservation create(HeldSeats hold, Long amount, OffsetDateTime performanceStartsAt, Clock clock) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime paymentDeadline = now.plusMinutes(PAYMENT_WINDOW_MINUTES);

        return builder()
                .memberId(hold.memberId())
                .scheduleId(hold.scheduleId())
                .seatIds(hold.seatIds())
                .amount(amount)
                .status(ReservationStatus.PENDING_PAYMENT)
                .expiresAt(paymentDeadline)
                .originalExpiresAt(paymentDeadline)
                .performanceStartsAt(performanceStartsAt)
                .reservedAt(now)
                .paymentAttemptNo(FIRST_PAYMENT_ATTEMPT)
                .build();
    }

    static Builder builder() {
        return new Builder();
    }

    boolean isCancelled() {
        return status == ReservationStatus.CANCELLED;
    }

    boolean isExpired(Clock clock) {
        return status == ReservationStatus.EXPIRED || !OffsetDateTime.now(clock).isBefore(expiresAt);
    }

    boolean isPendingPayment() {
        return status == ReservationStatus.PENDING_PAYMENT;
    }

    boolean isCancellationClosed(Clock clock) {
        return !OffsetDateTime.now(clock).isBefore(performanceStartsAt);
    }

    boolean isPaymentInProgress() {
        return paymentStartsAt != null;
    }

    void cancel(Clock clock) {
        status = ReservationStatus.CANCELLED;
        cancelledAt = OffsetDateTime.now(clock);
    }

    void startNextPaymentAttempt() {
        paymentAttemptNo++;
    }

    boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    String currentOrderId() {
        return ORDER_ID_PREFIX + id + ORDER_ID_SEPARATOR + paymentAttemptNo;
    }

    Long id() {
        return id;
    }

    Long scheduleId() {
        return scheduleId;
    }

    List<Long> seatIds() {
        return seatIds;
    }

    Long amount() {
        return amount;
    }

    ReservationStatus status() {
        return status;
    }

    int paymentAttemptNo() {
        return paymentAttemptNo;
    }

    OffsetDateTime expiresAt() {
        return expiresAt;
    }

    OffsetDateTime originalExpiresAt() {
        return originalExpiresAt;
    }

    OffsetDateTime reservedAt() {
        return reservedAt;
    }

    OffsetDateTime cancelledAt() {
        return cancelledAt;
    }

    static class Builder {

        private Long id;
        private Long memberId;
        private Long scheduleId;
        private List<Long> seatIds;
        private Long amount;
        private OffsetDateTime originalExpiresAt;
        private OffsetDateTime performanceStartsAt;
        private OffsetDateTime reservedAt;
        private ReservationStatus status;
        private OffsetDateTime expiresAt;
        private int paymentAttemptNo;
        private OffsetDateTime paymentStartsAt;
        private OffsetDateTime cancelledAt;

        Builder id(Long id) {
            this.id = id;
            return this;
        }

        Builder memberId(Long memberId) {
            this.memberId = memberId;
            return this;
        }

        Builder scheduleId(Long scheduleId) {
            this.scheduleId = scheduleId;
            return this;
        }

        Builder seatIds(List<Long> seatIds) {
            this.seatIds = seatIds;
            return this;
        }

        Builder amount(Long amount) {
            this.amount = amount;
            return this;
        }

        Builder originalExpiresAt(OffsetDateTime originalExpiresAt) {
            this.originalExpiresAt = originalExpiresAt;
            return this;
        }

        Builder performanceStartsAt(OffsetDateTime performanceStartsAt) {
            this.performanceStartsAt = performanceStartsAt;
            return this;
        }

        Builder reservedAt(OffsetDateTime reservedAt) {
            this.reservedAt = reservedAt;
            return this;
        }

        Builder status(ReservationStatus status) {
            this.status = status;
            return this;
        }

        Builder expiresAt(OffsetDateTime expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        Builder paymentAttemptNo(int paymentAttemptNo) {
            this.paymentAttemptNo = paymentAttemptNo;
            return this;
        }

        Builder paymentStartsAt(OffsetDateTime paymentStartsAt) {
            this.paymentStartsAt = paymentStartsAt;
            return this;
        }

        Builder cancelledAt(OffsetDateTime cancelledAt) {
            this.cancelledAt = cancelledAt;
            return this;
        }

        Reservation build() {
            return new Reservation(this);
        }
    }
}
