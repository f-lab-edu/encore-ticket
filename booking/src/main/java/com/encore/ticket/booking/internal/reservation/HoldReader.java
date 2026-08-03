package com.encore.ticket.booking.internal.reservation;

interface HoldReader {
    HeldSeats findByHoldId(String holdId);
}
