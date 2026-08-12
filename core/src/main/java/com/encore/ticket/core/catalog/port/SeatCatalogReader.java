package com.encore.ticket.core.catalog.port;

import java.util.List;
import java.util.Map;
import com.encore.ticket.core.catalog.domain.SeatInfo;

public interface SeatCatalogReader {
    public boolean seatBelongsToSchedule(long scheduleId, long seatId);
    public Map<Long, Long> pricesOf(List<Long> seatIds);
    public List<SeatInfo> seatsOf(long scheduleId);
    public List<SeatInfo> seatsByIds(List<Long> seatIds);
}
