package com.encore.ticket.core.catalog.domain;

public record SeatInfo(
        Long id,
        Long scheduleId,
        String section,
        String row,
        String number,
        String grade,
        Long price
) {
}
