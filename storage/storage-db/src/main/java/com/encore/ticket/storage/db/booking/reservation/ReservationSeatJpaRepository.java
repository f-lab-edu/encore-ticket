package com.encore.ticket.storage.db.booking.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface ReservationSeatJpaRepository extends JpaRepository<ReservationSeatEntity, ReservationSeatId> {

    List<ReservationSeatEntity> findByReservationId(Long reservationId);

    List<ReservationSeatEntity> findByReservationIdIn(Collection<Long> reservationIds);
}
