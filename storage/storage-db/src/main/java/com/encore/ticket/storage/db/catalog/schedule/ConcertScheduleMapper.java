package com.encore.ticket.storage.db.catalog.schedule;

import com.encore.ticket.core.catalog.domain.ConcertSchedule;

public final class ConcertScheduleMapper {
    private ConcertScheduleMapper() {}
    public static ConcertSchedule toDomain(ConcertScheduleEntity entity) {
        return new ConcertSchedule(
                entity.id(),
                entity.startsAt(),
                entity.endsAt(),
                entity.bookingOpensAt(),
                entity.bookingClosesAt(),
                entity.status());
    }
}
