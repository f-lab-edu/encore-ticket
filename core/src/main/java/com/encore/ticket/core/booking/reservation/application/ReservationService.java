package com.encore.ticket.core.booking.reservation.application;

import com.encore.ticket.core.booking.dto.ReservationCancelResponse;
import com.encore.ticket.core.booking.exception.CancellationClosedException;
import com.encore.ticket.core.booking.exception.PaymentInProgressException;
import com.encore.ticket.core.booking.exception.ReservationConcurrentModificationException;
import com.encore.ticket.core.booking.exception.ReservationNotOwnedException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final int MAX_CANCELLATION_ATTEMPTS = 2;
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public CancelResult cancel(Long reservationId, Long memberId) {
        for (int attempt = 0; attempt < MAX_CANCELLATION_ATTEMPTS; attempt++) {
            Reservation reservation = reservationRepository.getById(reservationId);

            validateOwnership(reservation, memberId);
            if (reservation.isCancelled()) {
                return new CancelResult(null, true);
            }
            validateCancellable(reservation);

            try {
                Reservation cancelled = reservationRepository.saveCancelled(reservation.cancel(clock));
                return new CancelResult(new ReservationCancelResponse(
                        cancelled.id(), cancelled.status(), displayed(cancelled.cancelledAt())), false);
            } catch (ReservationConcurrentModificationException exception) {
                if (attempt == MAX_CANCELLATION_ATTEMPTS - 1) {
                    throw exception;
                }
            }
        }

        throw new IllegalStateException("예매 취소 재시도 횟수 계산이 잘못되었습니다.");
    }

    private void validateOwnership(Reservation reservation, Long memberId) {
        if (!reservation.isOwnedBy(memberId)) {
            throw new ReservationNotOwnedException();
        }
    }

    private void validateCancellable(Reservation reservation) {
        if (!reservation.isPendingPayment() || reservation.isCancellationClosed(clock)) {
            throw new CancellationClosedException();
        }
        if (reservation.isPaymentInProgress()) {
            throw new PaymentInProgressException();
        }
    }

    private static OffsetDateTime displayed(OffsetDateTime dateTime) {
        return dateTime.withOffsetSameInstant(KST).truncatedTo(ChronoUnit.SECONDS);
    }
}
