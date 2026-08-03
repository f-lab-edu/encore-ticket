package com.encore.ticket.payment.internal;

import java.util.Optional;

interface PaymentRepository {

    Optional<Payment> findByPaymentKey(String paymentKey);

    Optional<Payment> findByOrderId(String orderId);

    void save(Payment payment);
}
