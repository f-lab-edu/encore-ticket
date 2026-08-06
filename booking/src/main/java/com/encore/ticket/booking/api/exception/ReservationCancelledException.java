package com.encore.ticket.booking.api.exception;

public class ReservationCancelledException extends BookingException {

    public ReservationCancelledException() {
        super(BookingErrorCode.RESERVATION_CANCELLED, "이미 취소된 예매입니다.");
    }
}
