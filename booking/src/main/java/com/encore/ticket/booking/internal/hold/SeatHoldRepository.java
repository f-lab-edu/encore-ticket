package com.encore.ticket.booking.internal.hold;

import java.util.Set;

interface SeatHoldRepository {

    Set<Long> findOccupiedSeatIds(Long scheduleId);

    int countActiveSeatsOf(Long scheduleId, Long memberId);

    void save(SeatHold seatHold);
}
