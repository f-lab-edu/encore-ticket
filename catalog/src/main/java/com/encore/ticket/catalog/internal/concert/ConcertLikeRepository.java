package com.encore.ticket.catalog.internal.concert;

interface ConcertLikeRepository {
    boolean exists(long concertId, long memberId);

    void save(long concertId, long memberId);

    void delete(long concertId, long memberId);
}
