package com.encore.ticket.storage.db.payment;

import com.encore.ticket.core.payment.domain.Payment;
import com.encore.ticket.core.payment.port.PaymentRepository;

import org.springframework.stereotype.Repository;

import java.util.Optional;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpa;

    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        return paymentJpa.findByOrderId(orderId).map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByPaymentKey(String paymentKey) {
        return paymentJpa.findByPaymentKey(paymentKey).map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findLatestByHoldId(String holdId) {
        return paymentJpa.findFirstByHoldIdOrderByIdDesc(holdId).map(PaymentMapper::toDomain);
    }

    @Override
    public void save(Payment payment) {
        paymentJpa.save(PaymentMapper.toEntity(payment));
    }
}
