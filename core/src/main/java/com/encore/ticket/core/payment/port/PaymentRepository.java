package com.encore.ticket.core.payment.port;

import java.util.Optional;

import com.encore.ticket.core.exception.NotFoundException;
import com.encore.ticket.core.payment.domain.Payment;
import java.time.OffsetDateTime;
import java.util.List;

public interface PaymentRepository {

    Optional<Payment> findByOrderId(String orderId);

    default Payment getByOrderId(String orderId) {
        return findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 주문입니다: " + orderId));
    }

    Optional<Payment> findByPaymentKey(String paymentKey);

    Optional<Payment> findLatestByHoldId(String holdId);

    Optional<Payment> findCompletedByReservationId(Long reservationId);

    void save(Payment payment);

    PaymentStartResult start(PaymentStartCommand command);

    PaymentSettlementResult settle(PaymentSettlementCommand command);

    Payment decline(String paymentKey, String orderId, String reason);

    List<Payment> findPendingForRecovery(OffsetDateTime before, int batchSize);
}
