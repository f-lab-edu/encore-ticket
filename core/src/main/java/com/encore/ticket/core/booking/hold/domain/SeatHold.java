package com.encore.ticket.core.booking.hold.domain;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SeatHold(
        String holdId,
        Long scheduleId,
        List<Long> seatIds,
        Long memberId,
        OffsetDateTime expiresAt
        ) {

    private static final int HOLD_MINUTES = 7;
    private static final String HOLD_ID_PREFIX = "hold_";

    public static SeatHold hold(Long scheduleId, List<Long> seatIds, Long memberId, Clock clock) {
        return new SeatHold(
                HOLD_ID_PREFIX + UUID.randomUUID(),
                scheduleId,
                seatIds,
                memberId,
                OffsetDateTime.now(clock).plusMinutes(HOLD_MINUTES));
    }
}
