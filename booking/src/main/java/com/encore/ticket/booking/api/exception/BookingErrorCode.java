package com.encore.ticket.booking.api.exception;

public enum BookingErrorCode {

    QUEUE_NOT_ADMITTED,
    HOLD_NOT_OWNED,
    SEAT_ALREADY_HELD,
    IDEMPOTENCY_KEY_REUSED,
    PURCHASE_LIMIT_EXCEEDED,
    RESERVATION_CANCELLED,
    HOLD_EXPIRED
}
