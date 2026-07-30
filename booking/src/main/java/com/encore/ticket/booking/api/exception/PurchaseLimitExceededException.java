package com.encore.ticket.booking.api.exception;

public class PurchaseLimitExceededException extends BookingException {

    public PurchaseLimitExceededException() {
        super(BookingErrorCode.PURCHASE_LIMIT_EXCEEDED, "회차당 예매 가능한 좌석 수를 초과했습니다.");
    }
}
