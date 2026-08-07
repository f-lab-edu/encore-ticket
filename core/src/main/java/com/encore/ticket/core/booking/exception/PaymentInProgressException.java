package com.encore.ticket.core.booking.exception;

public class PaymentInProgressException extends BookingException {

    public PaymentInProgressException() {
        super(BookingErrorCode.PAYMENT_IN_PROGRESS, "결제 처리가 끝난 뒤에 다시 시도해 주세요.");
    }
}
