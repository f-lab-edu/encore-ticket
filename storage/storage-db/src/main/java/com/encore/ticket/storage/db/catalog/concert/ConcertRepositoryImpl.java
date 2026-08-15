package com.encore.ticket.storage.db.catalog.concert;

import com.encore.ticket.core.catalog.domain.Concert;
import com.encore.ticket.core.catalog.port.ConcertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ConcertRepositoryImpl implements ConcertRepository {

    private final ConcertJpaRepository concertJpa;

    @Override
    public Optional<Concert> findById(long concertId) {
        return concertJpa.findById(concertId).map(ConcertMapper::toDomain);
    }

    @Override
    public void save(Concert concert) {
        concertJpa.save(ConcertMapper.toEntity(concert));
    }

    @Override
    public List<Concert> findPage(int page, int size) {
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "id"));

        return concertJpa.findAllBy(pageRequest).stream()
                .map(ConcertMapper::toDomain)
                .toList();
    }

    @Override
    public long count() {
        return concertJpa.count();
    }


}
