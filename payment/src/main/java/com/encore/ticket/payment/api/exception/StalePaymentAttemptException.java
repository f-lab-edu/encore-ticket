package com.encore.ticket.payment.api.exception;

public class StalePaymentAttemptException extends PaymentException {

    public StalePaymentAttemptException() {
        super(PaymentErrorCode.STALE_PAYMENT_ATTEMPT, "더 최근의 결제 시도가 있어 처리할 수 없습니다.");
    }
}
