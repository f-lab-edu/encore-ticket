package com.encore.ticket.core.catalog;

public record SeatInfo(
        Long id,
        String section,
        String row,
        String number,
        String grade,
        Long price
) {
}
