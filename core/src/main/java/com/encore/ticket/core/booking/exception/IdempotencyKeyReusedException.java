package com.encore.ticket.core.booking.exception;

public class IdempotencyKeyReusedException extends BookingException {

    public IdempotencyKeyReusedException() {
        super(BookingErrorCode.IDEMPOTENCY_KEY_REUSED, "같은 요청 키로 다른 좌석 조합을 요청했습니다.");
    }
}
