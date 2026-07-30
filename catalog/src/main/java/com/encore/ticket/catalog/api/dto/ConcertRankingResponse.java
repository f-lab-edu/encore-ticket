package com.encore.ticket.catalog.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ConcertRankingResponse(
        OffsetDateTime asOf,
        List<Item> items) {

    public record Item(
            int rank,
            long concertId,
            String title,
            String posterUrl,
            int score) {
    }
}
