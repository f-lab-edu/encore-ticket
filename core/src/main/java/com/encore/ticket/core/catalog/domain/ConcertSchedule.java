package com.encore.ticket.core.catalog.domain;

import com.encore.ticket.core.catalog.dto.ConcertStatus;

import java.time.OffsetDateTime;

public record ConcertSchedule(
        Long id,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime bookingOpensAt,
        OffsetDateTime bookingClosesAt,
        ConcertStatus status) {
}
