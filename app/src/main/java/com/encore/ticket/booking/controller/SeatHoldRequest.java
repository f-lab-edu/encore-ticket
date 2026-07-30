package com.encore.ticket.booking.controller;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@DistinctSeatIds
record SeatHoldRequest(
        @NotNull Long scheduleId,
        @NotNull @Size(min = 1, max = SeatHoldRequest.MAX_SEATS_PER_REQUEST) List<Long> seatIds) {

    static final int MAX_SEATS_PER_REQUEST = 4;
}
