package com.encore.ticket.core.payment.port;

import com.encore.ticket.core.payment.domain.PaymentRefund;
import com.encore.ticket.core.payment.domain.PaymentRefundRecovery;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRefundRepository {
    Optional<PaymentRefund> findByPaymentId(Long paymentId);
    Optional<PaymentRefundClaim> tryClaim(Long paymentId);
    boolean markRequestStarted(PaymentRefundClaim claim);
    PaymentRefund finishClaim(PaymentRefundClaim claim, PaymentCancellation cancellation,
                              PaymentRefundRecovery recovery);
    void releaseClaim(PaymentRefundClaim claim);
    PaymentRefund updateRecovery(PaymentRefund expected, PaymentRefundRecovery recovery);
    PaymentRefund fail(PaymentRefund refund, String reason);
    /** 미해결 환불의 결과 조회 대상. 환불 재전송 시각 및 횟수와 별도로 선택한다. */
    List<PaymentRefund> findForResultRecovery(OffsetDateTime before, int batchSize);
}
