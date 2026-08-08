package com.encore.ticket.storage.db.catalog.reader;

import com.encore.ticket.core.catalog.domain.SeatInfo;
import com.encore.ticket.core.catalog.port.SeatCatalogReader;
import com.encore.ticket.storage.db.catalog.price.ConcertPriceEntity;
import com.encore.ticket.storage.db.catalog.price.ConcertPriceJpaRepository;
import com.encore.ticket.storage.db.catalog.schedule.ConcertScheduleEntity;
import com.encore.ticket.storage.db.catalog.schedule.ConcertScheduleJpaRepository;
import com.encore.ticket.storage.db.catalog.seat.SeatEntity;
import com.encore.ticket.storage.db.catalog.seat.SeatJpaRepository;
import com.encore.ticket.storage.db.catalog.seat.SeatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SeatCatalogReaderImpl implements SeatCatalogReader {
    private final SeatJpaRepository seatJpa;
    private final ConcertScheduleJpaRepository scheduleJpa;
    private final ConcertPriceJpaRepository priceJpa;

    @Override
    public boolean seatBelongsToSchedule(long scheduleId, long seatId) {
        return seatJpa.existsByIdAndScheduleId(seatId, scheduleId);
    }

    @Override
    public Map<Long, Long> pricesOf(List<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return Map.of();
        }

        List<SeatEntity> seats = seatJpa.findByIdIn(seatIds);

        return priceMapOf(seats);
    }

    @Override
    public List<SeatInfo> seatsOf(long scheduleId) {

        return toSeatInfos(seatJpa.findAllByScheduleId(scheduleId));
    }

    @Override
    public List<SeatInfo> seatsByIds(List<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return List.of();
        }

        return toSeatInfos(seatJpa.findByIdIn(seatIds));
    }

    private Map<Long, Long> priceMapOf(List<SeatEntity> seats) {
        if (seats.isEmpty()) {
            return Map.of();
        }

        List<Long> scheduleIds = seats.stream()
                .map(SeatEntity::scheduleId)
                .distinct()
                .toList();

        Map<Long, ConcertScheduleEntity> schedulesById = scheduleJpa.findByIdIn(scheduleIds).stream()
                .collect(Collectors.toMap(
                        ConcertScheduleEntity::id,
                        Function.identity()
                ));

        List<Long> concertIds = schedulesById.values().stream()
                .map(ConcertScheduleEntity::concertId)
                .distinct()
                .toList();

        Map<Long, Map<String, Long>> pricesByConcert = priceJpa.findByConcertIdIn(concertIds).stream()
                .collect(Collectors.groupingBy(
                        ConcertPriceEntity::concertId,
                        Collectors.toMap(
                                ConcertPriceEntity::grade,
                                ConcertPriceEntity::price
                        )
                ));

        Map<Long, Long> pricesBySeat = new HashMap<>();
        for (SeatEntity seat : seats) {
            ConcertScheduleEntity schedule = schedulesById.get(seat.scheduleId());
            if (schedule == null) {
                continue;
            }

            Map<String, Long> pricesByGrade = pricesByConcert.get(schedule.concertId());
            if (pricesByGrade == null) {
                continue;
            }

            Long price = pricesByGrade.get(seat.grade());
            if (price != null) {
                pricesBySeat.put(seat.id(), price);
            }
        }

        return Map.copyOf(pricesBySeat);
    }

    private List<SeatInfo> toSeatInfos(List<SeatEntity> seats) {
        Map<Long, Long> prices = priceMapOf(seats);

        return seats.stream()
                .filter(seat -> prices.containsKey(seat.id()))
                .map(seat -> SeatMapper.toDomain(
                        seat,
                        prices.get(seat.id())
                ))
                .toList();
    }
}
