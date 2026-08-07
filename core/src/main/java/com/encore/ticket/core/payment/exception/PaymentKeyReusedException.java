package com.encore.ticket.core.payment.exception;

public class PaymentKeyReusedException extends PaymentException {

    public PaymentKeyReusedException() {
        super(PaymentErrorCode.PAYMENT_KEY_REUSED, "같은 결제 키가 다른 주문에 이미 사용되었습니다.");
    }
}
