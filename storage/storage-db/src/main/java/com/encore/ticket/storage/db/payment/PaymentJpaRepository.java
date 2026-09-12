package com.encore.ticket.storage.db.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;
import java.time.OffsetDateTime;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.orderId = :orderId")
    Optional<PaymentEntity> findByOrderIdForUpdate(@Param("orderId") String orderId);

    Optional<PaymentEntity> findByPaymentKey(String paymentKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.paymentKey = :key")
    Optional<PaymentEntity> findByPaymentKeyForUpdate(@Param("key") String key);

    Optional<PaymentEntity> findFirstByHoldIdOrderByIdDesc(String holdId);

    Optional<PaymentEntity> findFirstByReservationIdAndStatusOrderByIdDesc(
            Long reservationId, com.encore.ticket.core.payment.dto.PaymentStatus status);

    @Query(value = """
            SELECT *
            FROM payment
            WHERE status = 'PENDING'
              AND created_at <= :before
              AND (last_recovery_at IS NULL OR last_recovery_at <= :before)
            ORDER BY COALESCE(last_recovery_at, created_at), id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PaymentEntity> findPendingForRecovery(
            @Param("before") OffsetDateTime before,
            @Param("batchSize") int batchSize);

}
