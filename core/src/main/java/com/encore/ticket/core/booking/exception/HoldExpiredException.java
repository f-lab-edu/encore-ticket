package com.encore.ticket.core.booking.exception;

public class HoldExpiredException extends BookingException {

    public HoldExpiredException() {
        super(BookingErrorCode.HOLD_EXPIRED, "선점 또는 예매가 만료되었습니다.");
    }
}
