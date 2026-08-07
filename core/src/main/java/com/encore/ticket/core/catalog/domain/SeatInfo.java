package com.encore.ticket.core.catalog.domain;

public record SeatInfo(
        Long id,
        String section,
        String row,
        String number,
        String grade,
        Long price
) {
}
