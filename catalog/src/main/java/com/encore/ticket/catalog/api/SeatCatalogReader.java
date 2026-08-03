package com.encore.ticket.catalog.api;

import java.util.List;
import java.util.Map;

public interface SeatCatalogReader {
    boolean seatBelongsToSchedule(long scheduleId, long seatId);
    Map<Long, Long> pricesOf(List<Long> seatIds);
    List<SeatInfo> seatsOf(long scheduleId);
    List<SeatInfo> seatsByIds(List<Long> seatIds);
}
