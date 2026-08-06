package com.encore.ticket.catalog.internal.concert;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

interface ConcertRankingRepository {
    Optional<OffsetDateTime> latestSnapshotAt();

    List<ConcertScore> scoresAt(OffsetDateTime snapshotAt, int limit);

    List<ConcertScore> bookingOpenSoon(int limit);
}
