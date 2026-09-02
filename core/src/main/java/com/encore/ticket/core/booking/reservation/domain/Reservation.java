package com.encore.ticket.core.booking.reservation.domain;

import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.booking.exception.HoldExpiredException;
import com.encore.ticket.core.booking.exception.HoldNotOwnedException;
import com.encore.ticket.core.booking.exception.ReservationCancelledException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class Reservation {

    private static final String ORDER_ID_PREFIX = "reservation-";
    private static final String ORDER_ID_SEPARATOR = "-";
    private static final int FIRST_PAYMENT_ATTEMPT = 1;
    private static final int PAYMENT_WINDOW_MINUTES = 10;

    private final Long id;
    private final Long version;
    private final String holdId;
    private final Long memberId;
    private final Long scheduleId;
    private final List<Long> seatIds;
    private final Long amount;
    private final OffsetDateTime originalExpiresAt;
    private final OffsetDateTime performanceStartsAt;
    private final OffsetDateTime reservedAt;

    private final ReservationStatus status;
    private final OffsetDateTime expiresAt;
    private final int paymentAttemptNo;
    private final OffsetDateTime paymentStartsAt;
    private final OffsetDateTime cancelledAt;

    public static Reservation create(HeldSeats hold, Long amount, OffsetDateTime performanceStartsAt, Clock clock) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime paymentDeadline = now.plusMinutes(PAYMENT_WINDOW_MINUTES);

        return builder()
                .holdId(hold.holdId())
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

    public boolean isCancelled() {
        return status == ReservationStatus.CANCELLED;
    }

    public boolean isExpired(Clock clock) {
        return status == ReservationStatus.EXPIRED
                || (isPendingPayment() && !OffsetDateTime.now(clock).isBefore(expiresAt));
    }

    public void validatePaymentPreparation(Long memberId, Clock clock) {
        if (!isOwnedBy(memberId)) {
            throw new HoldNotOwnedException();
        }
        if (isCancelled()) {
            throw new ReservationCancelledException();
        }
        if (isExpired(clock)) {
            throw new HoldExpiredException();
        }
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

    public Reservation cancel(Clock clock) {
        return toBuilder()
                .status(ReservationStatus.CANCELLED)
                .cancelledAt(OffsetDateTime.now(clock))
                .build();
    }

    public Reservation expire(OffsetDateTime now) {
        if (!isPendingPayment() || now.isBefore(expiresAt)) {
            throw new IllegalStateException("만료 대상이 아닌 예매는 만료 처리할 수 없습니다: " + id);
        }

        return toBuilder()
                .status(ReservationStatus.EXPIRED)
                .build();
    }

    public Reservation startNextPaymentAttempt() {
        return toBuilder()
                .paymentAttemptNo(paymentAttemptNo + 1)
                .build();
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public String currentOrderId() {
        return ORDER_ID_PREFIX + id + ORDER_ID_SEPARATOR + paymentAttemptNo;
    }
}
