package com.encore.ticket.core.booking.exception;

public class CancellationClosedException extends BookingException {

    public CancellationClosedException() {
        super(BookingErrorCode.CANCELLATION_CLOSED, "취소할 수 없는 예매입니다.");
    }
}
