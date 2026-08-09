package com.encore.ticket.storage.db.booking.seat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SeatAssignmentJpaRepository extends JpaRepository<SeatAssignmentEntity, Long> {

    List<SeatAssignmentEntity> findByScheduleId(Long scheduleId);

    void deleteByReservationId(Long reservationId);
}
