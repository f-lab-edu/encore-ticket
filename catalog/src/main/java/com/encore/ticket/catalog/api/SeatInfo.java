package com.encore.ticket.catalog.api;

public record SeatInfo(
        Long id,
        String section,
        String row,
        String number,
        String grade,
        Long price
) {
}
