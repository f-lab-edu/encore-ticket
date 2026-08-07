package com.encore.ticket.core.catalog.port;

import java.util.List;
import java.util.Map;
import com.encore.ticket.core.catalog.domain.ScheduleInfo;

import java.util.Optional;

import com.encore.ticket.core.exception.NotFoundException;

public interface ScheduleCatalogReader {
    Optional<ScheduleInfo> findScheduleOf(long scheduleId);

    default ScheduleInfo scheduleOf(long scheduleId) {
        return findScheduleOf(scheduleId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회차입니다: " + scheduleId));
    }
    public Map<Long, ScheduleInfo> schedulesOf(List<Long> scheduleIds);
}
