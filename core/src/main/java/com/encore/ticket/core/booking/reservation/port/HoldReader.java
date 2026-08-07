package com.encore.ticket.core.booking.reservation.port;

import com.encore.ticket.core.booking.reservation.domain.HeldSeats;

public interface HoldReader {
    public HeldSeats findByHoldId(String holdId);
}
