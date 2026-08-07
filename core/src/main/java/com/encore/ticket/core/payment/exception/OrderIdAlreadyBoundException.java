package com.encore.ticket.core.payment.exception;

public class OrderIdAlreadyBoundException extends PaymentException {

    public OrderIdAlreadyBoundException() {
        super(PaymentErrorCode.ORDER_ID_ALREADY_BOUND, "이 주문에는 다른 결제 키가 이미 연결되어 있습니다.");
    }
}
