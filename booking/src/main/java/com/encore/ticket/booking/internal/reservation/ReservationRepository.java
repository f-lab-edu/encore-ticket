package com.encore.ticket.booking.internal.reservation;

import java.util.List;
import java.util.Optional;

interface ReservationRepository {
    Reservation findById(Long reservationId);

    Optional<Reservation> findByHoldId(String holdId);

    List<Reservation> findPageByMemberId(Long memberId, int page, int size);

    long countByMemberId(Long memberId);

    Reservation save(Reservation reservation);
}
