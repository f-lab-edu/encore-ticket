package com.encore.ticket.booking.internal.hold;

import java.time.OffsetDateTime;
import java.util.List;

record SeatHold(
        String holdId,
        Long scheduleId,
        List<Long> seatIds,
        Long memberId,
        OffsetDateTime expiresAt
        ) {
}
