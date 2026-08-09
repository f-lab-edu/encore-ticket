package com.encore.ticket.storage.db.booking.seat;

import com.encore.ticket.core.booking.seat.port.SeatAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SeatAssignmentRepositoryImpl implements SeatAssignmentRepository {
    private final SeatAssignmentJpaRepository seatAssignmentJpa;

    @Transactional
    @Override
    public void assign(List<Long> seatIds, Long reservationId, Long scheduleId) {
        List<SeatAssignmentEntity> entities = seatIds.stream()
                .map(seatId -> SeatAssignmentEntity.builder()
                        .seatId(seatId)
                        .reservationId(reservationId)
                        .scheduleId(scheduleId)
                        .build())
                .toList();

        seatAssignmentJpa.saveAll(entities);
    }

    @Transactional
    @Override
    public void release(Long reservationId) {
        seatAssignmentJpa.deleteByReservationId(reservationId);
    }

    @Override
    public Set<Long> assignedSeatIdsOf(Long scheduleId) {
        return seatAssignmentJpa.findByScheduleId(scheduleId).stream()
                .map(SeatAssignmentEntity::seatId)
                .collect(Collectors.toSet());
    }
}
