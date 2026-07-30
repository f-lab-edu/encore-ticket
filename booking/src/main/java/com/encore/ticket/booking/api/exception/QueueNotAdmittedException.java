package com.encore.ticket.booking.api.exception;

public class QueueNotAdmittedException extends BookingException {

    public QueueNotAdmittedException() {
        super(BookingErrorCode.QUEUE_NOT_ADMITTED, "입장이 허용된 대기열 토큰이 아닙니다.");
    }
}
