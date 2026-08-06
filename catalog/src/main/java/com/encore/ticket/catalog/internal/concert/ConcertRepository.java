package com.encore.ticket.catalog.internal.concert;

import java.util.List;

interface ConcertRepository {
    Concert findById(long concertId);

    void save(Concert concert);

    List<Concert> findPage(int page, int size);

    long count();
}
