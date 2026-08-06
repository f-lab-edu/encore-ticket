package com.encore.ticket.booking.api.exception;

public class ReservationNotOwnedException extends BookingException {

    public ReservationNotOwnedException() {
        super(BookingErrorCode.RESERVATION_NOT_OWNED, "다른 사용자의 예매입니다.");
    }
}
