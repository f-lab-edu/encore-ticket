package com.encore.ticket.core.payment.port;

import java.util.Optional;
import com.encore.ticket.core.payment.domain.Payment;

public interface PaymentRepository {
    public Payment getByOrderId(String orderId);


    public Optional<Payment> findByPaymentKey(String paymentKey);

    public Optional<Payment> findByOrderId(String orderId);

    public Optional<Payment> findLatestByHoldId(String holdId);

    public void save(Payment payment);
}
