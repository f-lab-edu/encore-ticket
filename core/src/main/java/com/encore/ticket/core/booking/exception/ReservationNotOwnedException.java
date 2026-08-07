package com.encore.ticket.core.booking.exception;

public class ReservationNotOwnedException extends BookingException {

    public ReservationNotOwnedException() {
        super(BookingErrorCode.RESERVATION_NOT_OWNED, "다른 사용자의 예매입니다.");
    }
}
