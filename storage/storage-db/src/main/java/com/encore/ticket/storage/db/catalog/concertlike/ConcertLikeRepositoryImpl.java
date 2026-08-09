package com.encore.ticket.storage.db.catalog.concertlike;

import com.encore.ticket.core.catalog.port.ConcertLikeRepository;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ConcertLikeRepositoryImpl implements ConcertLikeRepository {

    private final ConcertLikeJpaRepository concertLikeJpa;

    @Override
    public boolean exists(long concertId, long memberId) {
        return concertLikeJpa.existsById(ConcertLikeId.builder()
                .concertId(concertId)
                .memberId(memberId)
                .build());
    }

    @Override
    public int count(long concertId) {
        return Math.toIntExact(concertLikeJpa.countByConcertId(concertId));
    }

    @Override
    public void save(long concertId, long memberId) {
        concertLikeJpa.save(ConcertLikeEntity.builder()
                .concertId(concertId)
                .memberId(memberId)
                .build());
    }

    @Override
    public void delete(long concertId, long memberId) {
        concertLikeJpa.deleteById(ConcertLikeId.builder()
                .concertId(concertId)
                .memberId(memberId)
                .build());
    }
}
