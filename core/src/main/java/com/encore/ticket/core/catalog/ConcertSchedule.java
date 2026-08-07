package com.encore.ticket.core.catalog;

import com.encore.ticket.core.catalog.dto.ConcertStatus;

import java.time.OffsetDateTime;

record ConcertSchedule(
        Long id,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime bookingOpensAt,
        OffsetDateTime bookingClosesAt,
        ConcertStatus status) {
}
