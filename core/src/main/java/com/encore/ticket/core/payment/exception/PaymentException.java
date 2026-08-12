package com.encore.ticket.core.payment.exception;

public abstract class PaymentException extends RuntimeException {

    private final PaymentErrorCode errorCode;

    protected PaymentException(PaymentErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public PaymentErrorCode errorCode() {
        return errorCode;
    }
}
