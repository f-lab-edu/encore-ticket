package com.encore.ticket.payment.api.exception;

public class ReservationNotOwnedException extends PaymentException {

    public ReservationNotOwnedException() {
        super(PaymentErrorCode.RESERVATION_NOT_OWNED, "다른 사용자의 예매입니다.");
    }
}
