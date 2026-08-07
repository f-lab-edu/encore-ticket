package com.encore.ticket.core.catalog.port;

import java.util.List;
import java.util.Map;
import com.encore.ticket.core.catalog.domain.ConcertPrice;
import com.encore.ticket.core.catalog.domain.ConcertSchedule;

public interface ConcertScheduleRepository {
    Map<Long, List<ConcertSchedule>> schedulesOf(List<Long> concertIds);

    public Map<Long, Long> minPricesOf(List<Long> concertIds);

    public List<ConcertSchedule> schedulesOf(long concertId);

    public List<ConcertPrice> pricesOf(long concertId);
}
