package com.encore.ticket.storage.db.catalog.concert;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConcertJpaRepository extends JpaRepository<ConcertEntity, Long> {

    List<ConcertEntity> findAllBy(Pageable pageable);
}
