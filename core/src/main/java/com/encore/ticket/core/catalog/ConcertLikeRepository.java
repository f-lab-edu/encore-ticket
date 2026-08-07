package com.encore.ticket.core.catalog;

interface ConcertLikeRepository {
    boolean exists(long concertId, long memberId);

    void save(long concertId, long memberId);

    void delete(long concertId, long memberId);
}
