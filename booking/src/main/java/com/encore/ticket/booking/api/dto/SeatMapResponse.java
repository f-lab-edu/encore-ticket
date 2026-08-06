package com.encore.ticket.booking.api.dto;

import java.util.List;

public record SeatMapResponse(
        long scheduleId,
        List<Seat> seats) {

    public record Seat(
            long id,
            String section,
            String row,
            String number,
            String grade,
            long price,
            SeatStatus status) {
    }
}
