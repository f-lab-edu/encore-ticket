package com.encore.ticket.core.catalog;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

record ConcertPeriod(LocalDate startDate, LocalDate endDate, OffsetDateTime bookingOpensAt) {

    private static final Comparator<ConcertSchedule> BY_STARTS_AT = Comparator.comparing(ConcertSchedule::startsAt);

    static ConcertPeriod of(List<ConcertSchedule> schedules) {
        ConcertSchedule earliest = schedules.stream().min(BY_STARTS_AT).orElseThrow();
        ConcertSchedule latest = schedules.stream().max(BY_STARTS_AT).orElseThrow();

        return new ConcertPeriod(
                earliest.startsAt().toLocalDate(),
                latest.startsAt().toLocalDate(),
                earliest.bookingOpensAt());
    }
}
