package com.encore.ticket.core.payment.port;

import com.encore.ticket.core.payment.domain.PaymentRefund;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRefundRepository {
    Optional<PaymentRefund> findByPaymentId(Long paymentId);
    PaymentRefund complete(PaymentRefund refund, OffsetDateTime completedAt);
    PaymentRefund fail(PaymentRefund refund, String reason);
    List<PaymentRefund> findPendingForRecovery(OffsetDateTime before, int batchSize);
}
