package com.encore.ticket.storage.db.booking.reservation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

interface ReservationJpaRepository extends JpaRepository<ReservationEntity, Long> {

    Optional<ReservationEntity> findByHoldId(String holdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservationEntity r where r.holdId = :holdId")
    Optional<ReservationEntity> findByHoldIdForUpdate(String holdId);

    List<ReservationEntity> findByMemberId(Long memberId, Pageable pageable);

    long countByMemberId(Long memberId);
}
