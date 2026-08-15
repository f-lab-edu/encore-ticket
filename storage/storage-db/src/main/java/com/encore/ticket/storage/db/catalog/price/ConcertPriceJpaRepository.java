package com.encore.ticket.storage.db.catalog.price;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ConcertPriceJpaRepository extends JpaRepository<ConcertPriceEntity, ConcertPriceId> {
    List<ConcertPriceEntity> findByConcertId(Long concertId);
    List<ConcertPriceEntity> findByConcertIdIn(Collection<Long> concertIds);

}
