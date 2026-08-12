package com.encore.ticket.core.catalog.port;

import java.util.List;
import java.util.Map;
import com.encore.ticket.core.catalog.domain.ScheduleInfo;

public interface ScheduleCatalogReader {
    public ScheduleInfo scheduleOf(long scheduleId);
    public Map<Long, ScheduleInfo> schedulesOf(List<Long> scheduleIds);
}
