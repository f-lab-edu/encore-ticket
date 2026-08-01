package com.encore.ticket.booking.api.exception;

public class QueueTokenExpiredException extends BookingException {

    public QueueTokenExpiredException() {
        super(BookingErrorCode.QUEUE_TOKEN_EXPIRED, "대기열 토큰이 만료되었습니다.");
    }
}
