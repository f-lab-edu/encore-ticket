package com.encore.ticket.payment.api.exception;

public enum PaymentErrorCode {

    AMOUNT_MISMATCH,
    RESERVATION_NOT_OWNED,
    PAYMENT_KEY_REUSED,
    ORDER_ID_ALREADY_BOUND,
    RESERVATION_CANCELLED,
    HOLD_EXPIRED
}
