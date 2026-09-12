package com.encore.ticket.storage.db.booking.reservation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;

public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, Long> {

    Optional<ReservationEntity> findByHoldId(String holdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservationEntity r where r.holdId = :holdId")
    Optional<ReservationEntity> findByHoldIdForUpdate(String holdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservationEntity r where r.id = :id")
    Optional<ReservationEntity> findByIdForUpdate(@Param("id") Long id);

    List<ReservationEntity> findByMemberId(Long memberId, Pageable pageable);

    long countByMemberId(Long memberId);

    @Query(value = """
            SELECT *
            FROM reservation
            WHERE status = 'PENDING_PAYMENT'
              AND expires_at <= :now
              AND payment_starts_at IS NULL
            ORDER BY expires_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ReservationEntity> findExpiredForUpdate(OffsetDateTime now, int batchSize);
}
