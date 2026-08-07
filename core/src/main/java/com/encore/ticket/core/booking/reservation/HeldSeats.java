package com.encore.ticket.core.booking.reservation;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

record HeldSeats(String holdId, Long scheduleId, List<Long> seatIds, Long memberId, OffsetDateTime expiresAt) {

    boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    boolean isExpired(Clock clock) {
        return !OffsetDateTime.now(clock).isBefore(expiresAt);
    }
}
