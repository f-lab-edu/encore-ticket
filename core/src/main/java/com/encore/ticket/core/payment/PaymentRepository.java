package com.encore.ticket.core.payment;

import java.util.Optional;

interface PaymentRepository {
    Payment getByOrderId(String orderId);


    Optional<Payment> findByPaymentKey(String paymentKey);

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findLatestByHoldId(String holdId);

    void save(Payment payment);
}
