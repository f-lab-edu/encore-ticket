package com.encore.ticket.storage.db.catalog.reader;

import com.encore.ticket.core.catalog.domain.ConcertPrice;
import com.encore.ticket.core.catalog.domain.ConcertSchedule;
import com.encore.ticket.core.catalog.port.ConcertCatalogReader;
import com.encore.ticket.storage.db.catalog.price.ConcertPriceEntity;
import com.encore.ticket.storage.db.catalog.price.ConcertPriceJpaRepository;
import com.encore.ticket.storage.db.catalog.price.ConcertPriceMapper;
import com.encore.ticket.storage.db.catalog.schedule.ConcertScheduleEntity;
import com.encore.ticket.storage.db.catalog.schedule.ConcertScheduleJpaRepository;
import com.encore.ticket.storage.db.catalog.schedule.ConcertScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ConcertCatalogReaderImpl implements ConcertCatalogReader {
    private final ConcertScheduleJpaRepository scheduleJpa;
    private final ConcertPriceJpaRepository priceJpa;

    @Override
    public Map<Long, List<ConcertSchedule>> schedulesOf(List<Long> concertIds) {
        if (concertIds.isEmpty()) {
            return Map.of();
        }

        return scheduleJpa.findByConcertIdIn(concertIds).stream()
                .collect(Collectors.groupingBy(
                        ConcertScheduleEntity::concertId,
                        Collectors.mapping(
                                ConcertScheduleMapper::toDomain,
                                Collectors.toList()
                        )
                ));
    }

    @Override
    public Map<Long, Long> minPricesOf(List<Long> concertIds) {
        if (concertIds.isEmpty()) {
            return Map.of();
        }

        return priceJpa.findByConcertIdIn(concertIds).stream()
                .collect(Collectors.toMap(
                        ConcertPriceEntity::concertId,
                        ConcertPriceEntity::price,
                        Long::min
                ));
    }

    @Override
    public List<ConcertSchedule> schedulesOf(long concertId) {
        return scheduleJpa.findByConcertIdOrderByStartsAt(concertId).stream()
                .map(ConcertScheduleMapper::toDomain)
                .toList();
    }

    @Override
    public List<ConcertPrice> pricesOf(long concertId) {
        return priceJpa.findByConcertId(concertId).stream()
                .map(ConcertPriceMapper::toDomain)
                .toList();
    }

}
