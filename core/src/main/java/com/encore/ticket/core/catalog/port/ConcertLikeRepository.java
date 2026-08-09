package com.encore.ticket.core.catalog.port;

public interface ConcertLikeRepository {
    public boolean exists(long concertId, long memberId);

    public int count(long concertId);

    public void save(long concertId, long memberId);

    public void delete(long concertId, long memberId);
}
