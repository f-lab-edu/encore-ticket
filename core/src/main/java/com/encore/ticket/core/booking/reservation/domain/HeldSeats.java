package com.encore.ticket.core.booking.reservation.domain;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

public record HeldSeats(String holdId, Long scheduleId, List<Long> seatIds, Long memberId, OffsetDateTime expiresAt) {

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public boolean isExpired(Clock clock) {
        return !OffsetDateTime.now(clock).isBefore(expiresAt);
    }
}
