package com.encore.ticket.storage.db.booking.seat;

import com.encore.ticket.core.booking.seat.port.SeatAssignmentReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SeatAssignmentReaderImpl implements SeatAssignmentReader {

    private final SeatAssignmentJpaRepository seatAssignmentJpa;

    @Override
    public Set<Long> assignedSeatIdsOf(Long scheduleId) {
        return seatAssignmentJpa.findByScheduleId(scheduleId).stream()
                .map(SeatAssignmentEntity::seatId)
                .collect(Collectors.toSet());
    }
}
