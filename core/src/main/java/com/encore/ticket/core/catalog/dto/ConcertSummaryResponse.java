package com.encore.ticket.core.catalog.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ConcertSummaryResponse(
        long id,
        String title,
        String posterUrl,
        String venue,
        LocalDate performanceStartDate,
        LocalDate performanceEndDate,
        OffsetDateTime bookingOpensAt,
        ConcertStatus status,
        long minPrice) {
}
