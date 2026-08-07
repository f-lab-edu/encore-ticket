package com.encore.ticket.storage.db.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByOrderId(String orderId);

    Optional<PaymentEntity> findByPaymentKey(String paymentKey);

    Optional<PaymentEntity> findFirstByHoldIdOrderByIdDesc(String holdId);

}
