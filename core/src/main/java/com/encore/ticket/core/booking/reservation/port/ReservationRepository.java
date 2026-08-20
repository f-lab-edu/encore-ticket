package com.encore.ticket.core.booking.reservation.port;

import java.util.List;
import java.util.Optional;

import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.exception.NotFoundException;

public interface ReservationRepository {

    Optional<Reservation> findById(Long reservationId);

    default Reservation getById(Long reservationId) {
        return findById(reservationId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 예매입니다: " + reservationId));
    }

    Optional<Reservation> findByHoldId(String holdId);

    List<Reservation> findPageByMemberId(Long memberId, int page, int size);

    long countByMemberId(Long memberId);

    Reservation save(Reservation reservation);

    Reservation saveIssued(Reservation reservation);

    Reservation saveCancelled(Reservation cancelled);
}
