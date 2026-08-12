package com.encore.ticket.core.catalog.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ConcertDetailResponse(
        long id,
        String title,
        String description,
        String notice,
        String posterUrl,
        String venue,
        int likeCount,
        boolean liked,
        List<Schedule> schedules,
        List<Price> prices) {

    public record Schedule(
            long id,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            OffsetDateTime bookingOpensAt,
            OffsetDateTime bookingClosesAt,
            ConcertStatus status) {
    }

    public record Price(
            String grade,
            long price) {
    }
}
