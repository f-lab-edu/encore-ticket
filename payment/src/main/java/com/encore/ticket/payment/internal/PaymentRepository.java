package com.encore.ticket.payment.internal;

import java.util.Optional;

interface PaymentRepository {
    Payment getByOrderId(String orderId);


    Optional<Payment> findByPaymentKey(String paymentKey);

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findLatestByHoldId(String holdId);

    void save(Payment payment);
}
