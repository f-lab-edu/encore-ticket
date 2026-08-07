package com.encore.ticket.core.catalog.application;

import com.encore.ticket.core.catalog.dto.ConcertLikeResponse;
import com.encore.ticket.core.catalog.domain.Concert;
import com.encore.ticket.core.catalog.port.ConcertLikeRepository;
import com.encore.ticket.core.catalog.port.ConcertRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConcertLikeService {

    private final ConcertRepository concertRepository;
    private final ConcertLikeRepository concertLikeRepository;

    public ConcertLikeResult like(long concertId, long memberId) {
        Concert concert = concertRepository.findById(concertId);
        if (concertLikeRepository.exists(concertId, memberId)) {
            return new ConcertLikeResult(toResponse(concert, true), false);
        }

        concertLikeRepository.save(concertId, memberId);
        concert.addLike();
        concertRepository.save(concert);

        return new ConcertLikeResult(toResponse(concert, true), true);
    }

    public ConcertLikeResponse unlike(long concertId, long memberId) {
        Concert concert = concertRepository.findById(concertId);
        if (!concertLikeRepository.exists(concertId, memberId)) {
            return toResponse(concert, false);
        }

        concertLikeRepository.delete(concertId, memberId);
        concert.removeLike();
        concertRepository.save(concert);

        return toResponse(concert, false);
    }

    private ConcertLikeResponse toResponse(Concert concert, boolean liked) {
        return new ConcertLikeResponse(concert.id(), liked, concert.likeCount());
    }
}
