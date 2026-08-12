package com.encore.ticket.core.catalog.port;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import com.encore.ticket.core.catalog.domain.ConcertScore;

public interface ConcertRankingRepository {
    public Optional<OffsetDateTime> latestSnapshotAt();

    public List<ConcertScore> scoresAt(OffsetDateTime snapshotAt, int limit);

    public List<ConcertScore> bookingOpenSoon(int limit);
}
