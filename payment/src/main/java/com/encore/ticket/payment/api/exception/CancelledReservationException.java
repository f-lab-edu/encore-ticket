package com.encore.ticket.payment.api.exception;

public class CancelledReservationException extends PaymentException {

    public CancelledReservationException() {
        super(PaymentErrorCode.RESERVATION_CANCELLED, "이미 취소된 예매입니다.");
    }
}
