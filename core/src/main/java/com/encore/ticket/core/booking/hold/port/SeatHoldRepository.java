package com.encore.ticket.core.booking.hold.port;

import java.time.OffsetDateTime;
import java.util.Map;

import com.encore.ticket.core.booking.hold.domain.SeatHold;

public interface SeatHoldRepository {

    Map<Long, OffsetDateTime> holdExpiryBySeatId(Long scheduleId);

    SeatHoldAcquisition acquire(
            SeatHold seatHold,
            int maxSeatsPerSchedule,
            String idempotencyKey,
            String requestFingerprint);
}
