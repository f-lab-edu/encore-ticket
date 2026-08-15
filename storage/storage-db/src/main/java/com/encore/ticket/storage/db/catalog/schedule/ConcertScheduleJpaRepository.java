package com.encore.ticket.storage.db.catalog.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ConcertScheduleJpaRepository extends JpaRepository<ConcertScheduleEntity, Long> {
    List<ConcertScheduleEntity> findByConcertIdIn(Collection<Long> concertIds);

    List<ConcertScheduleEntity> findByConcertIdOrderByStartsAt(Long concertId);

    List<ConcertScheduleEntity> findByIdIn(Collection<Long> scheduleIds);
}
