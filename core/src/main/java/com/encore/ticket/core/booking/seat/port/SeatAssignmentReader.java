package com.encore.ticket.core.booking.seat.port;

import java.util.Set;

public interface SeatAssignmentReader {

    Set<Long> assignedSeatIdsOf(Long scheduleId);
}
