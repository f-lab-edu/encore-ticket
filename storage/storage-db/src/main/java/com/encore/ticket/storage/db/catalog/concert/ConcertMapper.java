package com.encore.ticket.storage.db.catalog.concert;

import com.encore.ticket.core.catalog.domain.Concert;

final class ConcertMapper {
    private ConcertMapper(){
    }

    static Concert toDomain(ConcertEntity entity) {
        return Concert.builder()
                .id(entity.id())
                .title(entity.title())
                .description(entity.description())
                .notice(entity.notice())
                .posterUrl(entity.posterUrl())
                .venue(entity.venue())
                .status(entity.status())
                .likeCount(entity.likeCount())
                .build();
    }

    static ConcertEntity toEntity(Concert concert) {
        return ConcertEntity.builder()
                .id(concert.id())
                .title(concert.title())
                .description(concert.description())
                .notice(concert.notice())
                .posterUrl(concert.posterUrl())
                .venue(concert.venue())
                .status(concert.status())
                .likeCount(concert.likeCount())
                .build();
    }
}
