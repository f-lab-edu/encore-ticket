package com.encore.ticket.catalog.internal.concert;

import java.util.List;
import java.util.Map;

interface ConcertScheduleRepository {
    Map<Long, List<ConcertSchedule>> schedulesOf(List<Long> concertIds);

    Map<Long, Long> minPricesOf(List<Long> concertIds);

    List<ConcertSchedule> schedulesOf(long concertId);

    List<ConcertPrice> pricesOf(long concertId);
}
