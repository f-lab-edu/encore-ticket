package com.encore.ticket.booking.controller;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record SeatHoldRequest(
        @NotNull Long scheduleId,
        @NotNull @Size(min = 1, max = StubReservations.MAX_SEATS_PER_REQUEST) List<Long> seatIds) {

    @AssertTrue(message = "좌석 ID는 중복될 수 없습니다.")
    boolean isSeatIdsDistinct() {
        return seatIds == null || seatIds.size() == Set.copyOf(seatIds).size();
    }
}
