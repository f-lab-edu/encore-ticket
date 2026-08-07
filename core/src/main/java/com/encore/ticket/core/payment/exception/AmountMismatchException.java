package com.encore.ticket.core.payment.exception;

public class AmountMismatchException extends PaymentException {

    public AmountMismatchException() {
        super(PaymentErrorCode.AMOUNT_MISMATCH, "요청 금액이 예매 금액과 다릅니다.");
    }
}
