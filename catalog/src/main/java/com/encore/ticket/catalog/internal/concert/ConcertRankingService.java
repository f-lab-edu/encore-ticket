package com.encore.ticket.catalog.internal.concert;

import com.encore.ticket.catalog.api.dto.ConcertRankingResponse;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

class ConcertRankingService {

    private static final int FIRST_RANK = 1;

    private final ConcertRankingRepository concertRankingRepository;

    ConcertRankingService(ConcertRankingRepository concertRankingRepository) {
        this.concertRankingRepository = concertRankingRepository;
    }

    ConcertRankingResponse ranking(int limit) {
        Optional<OffsetDateTime> snapshotAt = concertRankingRepository.latestSnapshotAt();
        if (snapshotAt.isEmpty()) {
            return new ConcertRankingResponse(null, rank(concertRankingRepository.bookingOpenSoon(limit)));
        }

        return new ConcertRankingResponse(
                snapshotAt.get(),
                rank(concertRankingRepository.scoresAt(snapshotAt.get(), limit)));
    }

    private List<ConcertRankingResponse.Item> rank(List<ConcertScore> scores) {
        List<ConcertScore> ordered = scores.stream()
                .sorted(Comparator.comparingInt(ConcertScore::score).reversed())
                .toList();

        return IntStream.range(0, ordered.size())
                .mapToObj(index -> toItem(FIRST_RANK + index, ordered.get(index)))
                .toList();
    }

    private ConcertRankingResponse.Item toItem(int rank, ConcertScore score) {
        return new ConcertRankingResponse.Item(
                rank, score.concertId(), score.title(), score.posterUrl(), score.score());
    }
}
