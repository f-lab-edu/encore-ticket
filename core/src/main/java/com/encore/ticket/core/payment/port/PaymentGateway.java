package com.encore.ticket.core.payment.port;

public interface PaymentGateway {

    PaymentApproval approve(String paymentKey, String orderId, Long amount);

    PaymentApproval query(String paymentKey);

    /**
     * 기존 전액 환불의 완료 결과를 조회한다. 환불 요청을 새로 실행하지 않는다.
     * 취소 기록이 없는 승인 결제는 NOT_CANCELED로 반환한다. 재실행 허용은 아니다.
     * 그 밖에 완료를 검증하지 못하면 PaymentGatewayException이다.
     */
    PaymentCancellation queryCancellation(String paymentKey, Long expectedAmount);

    PaymentCancellation cancel(
            String paymentKey, Long amount, String reason, String idempotencyKey);
}
