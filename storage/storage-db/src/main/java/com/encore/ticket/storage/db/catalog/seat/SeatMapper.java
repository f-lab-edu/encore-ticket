package com.encore.ticket.storage.db.catalog.seat;

import com.encore.ticket.core.catalog.domain.SeatInfo;

public final class SeatMapper {
    private SeatMapper() {}

    public static SeatInfo toDomain(SeatEntity entity, Long price) {
        return new SeatInfo(
                entity.id(), entity.scheduleId(), entity.section(), entity.row(),
                entity.number(), entity.grade(), price);
    }
}
