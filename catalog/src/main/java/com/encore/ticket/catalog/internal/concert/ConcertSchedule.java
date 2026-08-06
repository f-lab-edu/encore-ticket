package com.encore.ticket.catalog.internal.concert;

import com.encore.ticket.catalog.api.dto.ConcertStatus;

import java.time.OffsetDateTime;

record ConcertSchedule(
        Long id,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime bookingOpensAt,
        OffsetDateTime bookingClosesAt,
        ConcertStatus status) {
}
