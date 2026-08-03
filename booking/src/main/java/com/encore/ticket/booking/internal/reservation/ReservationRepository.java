package com.encore.ticket.booking.internal.reservation;

interface ReservationRepository {
    Reservation findById(Long reservationId);

    void save(Reservation reservation);
}
