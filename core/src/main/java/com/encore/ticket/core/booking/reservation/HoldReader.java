package com.encore.ticket.core.booking.reservation;

interface HoldReader {
    HeldSeats findByHoldId(String holdId);
}
