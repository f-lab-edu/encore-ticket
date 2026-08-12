package com.encore.ticket.core.booking.reservation.domain;

import com.encore.ticket.core.booking.dto.ReservationStatus;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

public class Reservation {

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

    public static Reservation create(HeldSeats hold, Long amount, OffsetDateTime performanceStartsAt, Clock clock) {
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

    public static Builder builder() {
        return new Builder();
    }

    public boolean isCancelled() {
        return status == ReservationStatus.CANCELLED;
    }

    public boolean isExpired(Clock clock) {
        return status == ReservationStatus.EXPIRED || !OffsetDateTime.now(clock).isBefore(expiresAt);
    }

    public boolean isPendingPayment() {
        return status == ReservationStatus.PENDING_PAYMENT;
    }

    public boolean isCancellationClosed(Clock clock) {
        return !OffsetDateTime.now(clock).isBefore(performanceStartsAt);
    }

    public boolean isPaymentInProgress() {
        return paymentStartsAt != null;
    }

    public void cancel(Clock clock) {
        status = ReservationStatus.CANCELLED;
        cancelledAt = OffsetDateTime.now(clock);
    }

    public void startNextPaymentAttempt() {
        paymentAttemptNo++;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public String currentOrderId() {
        return ORDER_ID_PREFIX + id + ORDER_ID_SEPARATOR + paymentAttemptNo;
    }

    public Long id() {
        return id;
    }

    public Long scheduleId() {
        return scheduleId;
    }

    public List<Long> seatIds() {
        return seatIds;
    }

    public Long amount() {
        return amount;
    }

    public ReservationStatus status() {
        return status;
    }

    public int paymentAttemptNo() {
        return paymentAttemptNo;
    }

    public OffsetDateTime expiresAt() {
        return expiresAt;
    }

    public OffsetDateTime originalExpiresAt() {
        return originalExpiresAt;
    }

    public OffsetDateTime reservedAt() {
        return reservedAt;
    }

    public OffsetDateTime cancelledAt() {
        return cancelledAt;
    }

    public static class Builder {

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

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder memberId(Long memberId) {
            this.memberId = memberId;
            return this;
        }

        public Builder scheduleId(Long scheduleId) {
            this.scheduleId = scheduleId;
            return this;
        }

        public Builder seatIds(List<Long> seatIds) {
            this.seatIds = seatIds;
            return this;
        }

        public Builder amount(Long amount) {
            this.amount = amount;
            return this;
        }

        public Builder originalExpiresAt(OffsetDateTime originalExpiresAt) {
            this.originalExpiresAt = originalExpiresAt;
            return this;
        }

        public Builder performanceStartsAt(OffsetDateTime performanceStartsAt) {
            this.performanceStartsAt = performanceStartsAt;
            return this;
        }

        public Builder reservedAt(OffsetDateTime reservedAt) {
            this.reservedAt = reservedAt;
            return this;
        }

        public Builder status(ReservationStatus status) {
            this.status = status;
            return this;
        }

        public Builder expiresAt(OffsetDateTime expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder paymentAttemptNo(int paymentAttemptNo) {
            this.paymentAttemptNo = paymentAttemptNo;
            return this;
        }

        public Builder paymentStartsAt(OffsetDateTime paymentStartsAt) {
            this.paymentStartsAt = paymentStartsAt;
            return this;
        }

        public Builder cancelledAt(OffsetDateTime cancelledAt) {
            this.cancelledAt = cancelledAt;
            return this;
        }

        public Reservation build() {
            return new Reservation(this);
        }
    }
}
