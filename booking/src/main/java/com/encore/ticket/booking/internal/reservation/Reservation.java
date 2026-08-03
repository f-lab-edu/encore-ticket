package com.encore.ticket.booking.internal.reservation;

import com.encore.ticket.booking.api.dto.ReservationStatus;

import java.time.Clock;
import java.time.OffsetDateTime;

class Reservation {

    private final Long id;
    private final Long memberId;
    private final OffsetDateTime performanceStartsAt;

    private ReservationStatus status;
    private OffsetDateTime paymentStartsAt;
    private OffsetDateTime cancelledAt;

    Reservation(Long id, Long memberId, OffsetDateTime performanceStartsAt, ReservationStatus status, OffsetDateTime paymentStartsAt, OffsetDateTime cancelledAt) {
        this.id = id;
        this.memberId = memberId;
        this.performanceStartsAt = performanceStartsAt;
        this.status = status;
        this.paymentStartsAt = paymentStartsAt;
        this.cancelledAt = cancelledAt;
    }

    boolean isCancelled() {
        return status == ReservationStatus.CANCELLED;
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

    boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    Long id() {
        return id;
    }

    ReservationStatus status() {
        return status;
    }


    OffsetDateTime cancelledAt() {
        return cancelledAt;
    }
}
