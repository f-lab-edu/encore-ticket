package com.encore.ticket.booking.controller;

import com.encore.ticket.booking.api.dto.ReservationStatus;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

record ReservationCancelRequest(
        @NotNull ReservationStatus status) {

    @AssertTrue(message = "status는 CANCELLED만 허용됩니다.")
    boolean isCancelRequest() {
        return status == null || status == ReservationStatus.CANCELLED;
    }
}
