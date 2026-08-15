package com.encore.ticket.core.booking.reservation.application;

import com.encore.ticket.core.booking.dto.ReservationCancelResponse;
import com.encore.ticket.core.booking.exception.CancellationClosedException;
import com.encore.ticket.core.booking.exception.PaymentInProgressException;
import com.encore.ticket.core.booking.exception.ReservationNotOwnedException;

import java.time.Clock;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public CancelResult cancel(Long reservationId, Long memberId) {
        Reservation reservation = reservationRepository.getById(reservationId);

        if (!reservation.isOwnedBy(memberId)) {
            throw new ReservationNotOwnedException();
        }
        if (reservation.isCancelled()) {
            return new CancelResult(null, true);
        }
        if (reservation.isCancellationClosed(clock)) {
            throw new CancellationClosedException();
        }
        if (reservation.isPaymentInProgress()) {
            throw new PaymentInProgressException();
        }

        reservation.cancel(clock);
        reservationRepository.save(reservation);

        return new CancelResult(new ReservationCancelResponse(reservation.id(), reservation.status(), reservation.cancelledAt()), false);
    }
}
