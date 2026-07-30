package com.encore.ticket.payment.api.exception;

public class ExpiredReservationException extends PaymentException {

    public ExpiredReservationException() {
        super(PaymentErrorCode.HOLD_EXPIRED, "예매가 만료되었습니다.");
    }
}
