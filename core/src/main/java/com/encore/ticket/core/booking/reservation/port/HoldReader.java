package com.encore.ticket.core.booking.reservation.port;

import com.encore.ticket.core.booking.reservation.domain.HeldSeats;

import java.util.Optional;

import com.encore.ticket.core.exception.NotFoundException;

public interface HoldReader {
    Optional<HeldSeats> findByHoldId(String holdId);

    default HeldSeats getByHoldId(String holdId) {
        return findByHoldId(holdId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 선점입니다: " + holdId));
    }
}
