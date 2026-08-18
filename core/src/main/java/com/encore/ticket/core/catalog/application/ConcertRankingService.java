package com.encore.ticket.core.catalog.application;

import com.encore.ticket.core.catalog.dto.ConcertRankingResponse;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import com.encore.ticket.core.catalog.domain.ConcertScore;
import com.encore.ticket.core.catalog.port.ConcertRankingRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConcertRankingService {

    private static final int FIRST_RANK = 1;

    private final ConcertRankingRepository concertRankingRepository;

    public ConcertRankingResponse ranking(int limit) {
        return concertRankingRepository.latestSnapshotAt()
                .map(snapshotAt -> new ConcertRankingResponse(
                        snapshotAt,
                        rank(concertRankingRepository.scoresAt(snapshotAt, limit))))
                .orElseGet(() -> new ConcertRankingResponse(
                        null,
                        rank(concertRankingRepository.bookingOpenSoon(limit))));
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
