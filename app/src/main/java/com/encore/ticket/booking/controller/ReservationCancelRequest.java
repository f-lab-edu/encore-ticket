package com.encore.ticket.booking.controller;

import com.encore.ticket.booking.api.dto.ReservationStatus;

import jakarta.validation.constraints.NotNull;

@CancelStatusOnly
record ReservationCancelRequest(
        @NotNull ReservationStatus status) {
}
