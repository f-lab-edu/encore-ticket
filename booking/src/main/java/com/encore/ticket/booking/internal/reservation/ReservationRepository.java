package com.encore.ticket.booking.internal.reservation;

import java.util.Optional;

interface ReservationRepository {
    Reservation findById(Long reservationId);

    Optional<Reservation> findByHoldId(String holdId);

    Reservation save(Reservation reservation);
}
