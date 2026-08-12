package com.encore.ticket.core.catalog.port;

import java.util.List;
import com.encore.ticket.core.catalog.domain.Concert;

public interface ConcertRepository {
    public Concert findById(long concertId);

    public void save(Concert concert);

    public List<Concert> findPage(int page, int size);

    public long count();
}
