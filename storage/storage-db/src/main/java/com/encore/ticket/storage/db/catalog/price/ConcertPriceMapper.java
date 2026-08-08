package com.encore.ticket.storage.db.catalog.price;

import com.encore.ticket.core.catalog.domain.ConcertPrice;

public final class ConcertPriceMapper {
    private ConcertPriceMapper() {

    }
    public static ConcertPrice toDomain(ConcertPriceEntity entity) {
        return new ConcertPrice(entity.grade(), entity.price());
    }
}
