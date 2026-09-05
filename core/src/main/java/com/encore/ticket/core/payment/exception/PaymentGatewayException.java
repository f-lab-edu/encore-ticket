package com.encore.ticket.core.payment.exception;

/** An indeterminate provider result; callers must keep the local payment pending. */
public class PaymentGatewayException extends RuntimeException {
    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
