package com.encore.ticket.booking.controller;

import java.util.List;

import com.encore.ticket.booking.api.dto.SeatMapResponse;
import com.encore.ticket.booking.api.dto.SeatStatus;

final class StubSeatMap {

    private static final long VIP_PRICE = 165_000L;

    private static final long R_PRICE = 115_000L;

    private static final long S_PRICE = 77_000L;

    private static final List<StubSeat> SEATS = List.of(
            new StubSeat(1, "A구역", "1열", "1번", "VIP", VIP_PRICE, SeatStatus.AVAILABLE),
            new StubSeat(2, "A구역", "1열", "2번", "VIP", VIP_PRICE, SeatStatus.HELD),
            new StubSeat(3, "A구역", "1열", "3번", "VIP", VIP_PRICE, SeatStatus.RESERVED),
            new StubSeat(4, "B구역", "1열", "1번", "R", R_PRICE, SeatStatus.AVAILABLE),
            new StubSeat(5, "B구역", "1열", "2번", "R", R_PRICE, SeatStatus.AVAILABLE),
            new StubSeat(6, "S구역", "1열", "1번", "S", S_PRICE, SeatStatus.AVAILABLE));

    private StubSeatMap() {
    }

    static SeatMapResponse of(long scheduleId) {
        List<SeatMapResponse.Seat> seats = SEATS.stream()
                .map(seat -> toSeat(scheduleId, seat))
                .toList();

        return new SeatMapResponse(scheduleId, seats);
    }

    private static SeatMapResponse.Seat toSeat(long scheduleId, StubSeat seat) {
        return new SeatMapResponse.Seat(
                scheduleId * 10 + seat.sequence(),
                seat.section(),
                seat.row(),
                seat.number(),
                seat.grade(),
                seat.price(),
                seat.status());
    }

    private record StubSeat(
            int sequence,
            String section,
            String row,
            String number,
            String grade,
            long price,
            SeatStatus status) {
    }
}
