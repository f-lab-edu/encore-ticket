package com.encore.ticket.storage.db.catalog.seat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SeatJpaRepository extends JpaRepository<SeatEntity, Long> {

    List<SeatEntity> findAllByScheduleId(Long scheduleId);

    List<SeatEntity> findByIdIn(Collection<Long> seatIds);

    boolean existsByIdAndScheduleId(Long seatId, Long scheduleId);

}
