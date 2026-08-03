package com.encore.ticket.booking.internal.hold;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

interface SeatHoldRepository {

    Set<Long> findOccupiedSeatIds(Long scheduleId);
    Map<Long, OffsetDateTime> holdExpiryBySeatId(Long scheduleId);
    Set<Long> reservedSeatIdsOf(Long scheduleId);

    int countActiveSeatsOf(Long scheduleId, Long memberId);

    void save(SeatHold seatHold);
}
