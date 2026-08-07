package com.encore.ticket.core.catalog;

import java.util.List;
import java.util.Map;

public interface ScheduleCatalogReader {
    ScheduleInfo scheduleOf(long scheduleId);
    Map<Long, ScheduleInfo> schedulesOf(List<Long> scheduleIds);
}
