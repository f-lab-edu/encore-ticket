package com.encore.ticket.core.booking.hold.port;

public enum SeatHoldAcquireResult {
    ACQUIRED,
    REPLAYED,
    SEAT_ALREADY_HELD,
    PURCHASE_LIMIT_EXCEEDED,
    IDEMPOTENCY_KEY_REUSED
}
