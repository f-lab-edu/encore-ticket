package com.encore.ticket.storage.db.payment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PaymentRefundJpaRepository extends JpaRepository<PaymentRefundEntity, Long> {

    Optional<PaymentRefundEntity> findByPaymentId(Long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRefundEntity r where r.idempotencyKey = :key")
    Optional<PaymentRefundEntity> findByIdempotencyKeyForUpdate(@Param("key") String key);

    @Query(value = """
            SELECT *
            FROM payment_refund
            WHERE status = 'PENDING'
              AND created_at <= :before
              AND (last_recovery_at IS NULL OR last_recovery_at <= :before)
            ORDER BY COALESCE(last_recovery_at, created_at), id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PaymentRefundEntity> findPendingForUpdate(
            @Param("before") OffsetDateTime before,
            @Param("batchSize") int batchSize);
}
