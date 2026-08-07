package com.encore.ticket.core.payment.exception;

import lombok.Getter;

@Getter
public abstract class PaymentException extends RuntimeException {

    private final PaymentErrorCode errorCode;

    protected PaymentException(PaymentErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
