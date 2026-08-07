package com.encore.ticket.core.booking.exception;

public class SeatAlreadyHeldException extends BookingException {

    public SeatAlreadyHeldException() {
        super(BookingErrorCode.SEAT_ALREADY_HELD, "이미 선점되거나 예매된 좌석입니다.");
    }
}
