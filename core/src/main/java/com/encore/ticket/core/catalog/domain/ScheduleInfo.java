package com.encore.ticket.core.catalog.domain;

import java.time.OffsetDateTime;

public record ScheduleInfo(
        Long id,
        OffsetDateTime startsAt,
        String venue,
        Long concertId,
        String concertTitle,
        String posterUrl) {
}
