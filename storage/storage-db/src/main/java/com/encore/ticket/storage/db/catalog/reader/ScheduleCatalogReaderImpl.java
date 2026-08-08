package com.encore.ticket.storage.db.catalog.reader;

import com.encore.ticket.core.catalog.domain.ScheduleInfo;
import com.encore.ticket.core.catalog.port.ScheduleCatalogReader;
import com.encore.ticket.storage.db.catalog.concert.ConcertEntity;
import com.encore.ticket.storage.db.catalog.concert.ConcertJpaRepository;
import com.encore.ticket.storage.db.catalog.schedule.ConcertScheduleEntity;
import com.encore.ticket.storage.db.catalog.schedule.ConcertScheduleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ScheduleCatalogReaderImpl implements ScheduleCatalogReader {
    private final ConcertScheduleJpaRepository scheduleJpa;
    private final ConcertJpaRepository concertJpa;

    @Override
    public Optional<ScheduleInfo> findScheduleOf(long scheduleId) {
        return scheduleJpa.findById(scheduleId)
                .flatMap(schedule -> concertJpa.findById(schedule.concertId())
                        .map(concert -> toScheduleInfo(schedule, concert)));
    }

    @Override
    public Map<Long, ScheduleInfo> schedulesOf(List<Long> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            return Map.of();
        }

        List<ConcertScheduleEntity> schedules = scheduleJpa.findByIdIn(scheduleIds);

        List<Long> concertIds = schedules.stream()
                .map(ConcertScheduleEntity::concertId)
                .distinct()
                .toList();

        Map<Long, ConcertEntity> concertsById = concertJpa.findAllById(concertIds).stream()
                .collect(Collectors.toMap(
                        ConcertEntity::id,
                        Function.identity()
                ));

        return schedules.stream()
                .filter(schedule -> concertsById.containsKey(schedule.concertId()))
                .collect(Collectors.toMap(
                        ConcertScheduleEntity::id,
                        schedule -> toScheduleInfo(schedule, concertsById.get(schedule.concertId()))
                ));
    }

    private ScheduleInfo toScheduleInfo(ConcertScheduleEntity schedule, ConcertEntity concert) {
        return new ScheduleInfo(
                schedule.id(),
                schedule.startsAt(),
                concert.venue(),
                concert.id(),
                concert.title(),
                concert.posterUrl()
        );
    }
}
