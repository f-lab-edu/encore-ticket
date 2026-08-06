package com.encore.ticket.catalog.internal.concert;

import com.encore.ticket.catalog.api.dto.ConcertLikeResponse;

class ConcertLikeService {

    private final ConcertRepository concertRepository;
    private final ConcertLikeRepository concertLikeRepository;

    ConcertLikeService(ConcertRepository concertRepository, ConcertLikeRepository concertLikeRepository) {
        this.concertRepository = concertRepository;
        this.concertLikeRepository = concertLikeRepository;
    }

    ConcertLikeResult like(long concertId, long memberId) {
        Concert concert = concertRepository.findById(concertId);
        if (concertLikeRepository.exists(concertId, memberId)) {
            return new ConcertLikeResult(toResponse(concert, true), false);
        }

        concertLikeRepository.save(concertId, memberId);
        concert.addLike();
        concertRepository.save(concert);

        return new ConcertLikeResult(toResponse(concert, true), true);
    }

    ConcertLikeResponse unlike(long concertId, long memberId) {
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
