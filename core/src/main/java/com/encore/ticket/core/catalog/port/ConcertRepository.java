package com.encore.ticket.core.catalog.port;

import java.util.List;
import com.encore.ticket.core.catalog.domain.Concert;

import java.util.Optional;

import com.encore.ticket.core.exception.NotFoundException;

public interface ConcertRepository {
    Optional<Concert> findById(long concertId);

    default Concert getById(long concertId) {
        return findById(concertId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 콘서트입니다: " + concertId));
    }

    public void save(Concert concert);

    public List<Concert> findPage(int page, int size);

    public long count();
}
