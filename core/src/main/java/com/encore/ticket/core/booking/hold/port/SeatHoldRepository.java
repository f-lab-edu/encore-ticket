package com.encore.ticket.core.booking.hold.port;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import com.encore.ticket.core.booking.hold.domain.SeatHold;

public interface SeatHoldRepository {

    public Set<Long> findOccupiedSeatIds(Long scheduleId);
    public Map<Long, OffsetDateTime> holdExpiryBySeatId(Long scheduleId);
    public Set<Long> reservedSeatIdsOf(Long scheduleId);

    public int countActiveSeatsOf(Long scheduleId, Long memberId);

    public void save(SeatHold seatHold);
}
