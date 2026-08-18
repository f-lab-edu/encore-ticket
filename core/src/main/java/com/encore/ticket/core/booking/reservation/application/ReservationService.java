package com.encore.ticket.core.booking.reservation.application;

import com.encore.ticket.core.booking.dto.ReservationCancelResponse;
import com.encore.ticket.core.booking.exception.CancellationClosedException;
import com.encore.ticket.core.booking.exception.PaymentInProgressException;
import com.encore.ticket.core.booking.exception.ReservationNotOwnedException;

import java.time.Clock;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;

import com.encore.ticket.core.booking.seat.port.SeatAssignmentRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
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

        Reservation cancelled = reservation.cancel(clock);
        reservationRepository.save(cancelled);
        seatAssignmentRepository.release(reservationId);

        return new CancelResult(new ReservationCancelResponse(cancelled.id(), cancelled.status(), cancelled.cancelledAt()), false);
    }
}
