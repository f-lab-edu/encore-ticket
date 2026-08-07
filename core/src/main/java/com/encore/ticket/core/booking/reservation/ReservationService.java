package com.encore.ticket.core.booking.reservation;

import com.encore.ticket.core.booking.dto.ReservationCancelResponse;
import com.encore.ticket.core.booking.exception.CancellationClosedException;
import com.encore.ticket.core.booking.exception.PaymentInProgressException;
import com.encore.ticket.core.booking.exception.ReservationNotOwnedException;

import java.time.Clock;

class ReservationService {

    private final ReservationRepository reservationRepository;
    private final Clock clock;

    ReservationService(ReservationRepository reservationRepository, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    CancelResult cancel(Long reservationId, Long memberId) {
        Reservation reservation = reservationRepository.findById(reservationId);

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
