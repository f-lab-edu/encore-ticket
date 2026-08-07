package com.encore.ticket.storage.db.booking.reservation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface ReservationJpaRepository extends JpaRepository<ReservationEntity, Long> {

    Optional<ReservationEntity> findByHoldId(String holdId);

    List<ReservationEntity> findByMemberId(Long memberId, Pageable pageable);

    long countByMemberId(Long memberId);
}
