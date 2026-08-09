package com.encore.ticket.core.booking.seat.port;

import java.util.List;
import java.util.Set;

public interface SeatAssignmentRepository {

    void assign(List<Long> seatIds, Long reservationId, Long scheduleId);

    void release(Long reservationId);

    Set<Long> assignedSeatIdsOf(Long scheduleId);
}
