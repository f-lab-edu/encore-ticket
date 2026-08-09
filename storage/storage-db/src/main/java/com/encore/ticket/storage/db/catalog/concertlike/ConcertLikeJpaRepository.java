package com.encore.ticket.storage.db.catalog.concertlike;

import org.springframework.data.jpa.repository.JpaRepository;

interface ConcertLikeJpaRepository extends JpaRepository<ConcertLikeEntity, ConcertLikeId> {

    long countByConcertId(Long concertId);
}
