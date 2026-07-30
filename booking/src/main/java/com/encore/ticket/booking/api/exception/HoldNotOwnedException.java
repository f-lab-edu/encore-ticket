package com.encore.ticket.booking.api.exception;

public class HoldNotOwnedException extends BookingException {

    public HoldNotOwnedException() {
        super(BookingErrorCode.HOLD_NOT_OWNED, "다른 사용자의 선점 정보입니다.");
    }
}
