package com.encore.ticket.catalog.internal.concert;

interface ConcertRepository {
    Concert findById(long concertId);

    void save(Concert concert);
}
