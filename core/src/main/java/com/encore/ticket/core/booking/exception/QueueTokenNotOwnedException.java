package com.encore.ticket.core.booking.exception;

public class QueueTokenNotOwnedException extends BookingException {

    public QueueTokenNotOwnedException() {
        super(BookingErrorCode.QUEUE_TOKEN_NOT_OWNED, "다른 사용자의 대기열 토큰입니다.");
    }
}
