package com.encore.ticket.booking.controller;

import com.encore.ticket.core.booking.dto.ReservationStatus;

import jakarta.validation.constraints.NotNull;

@CancelStatusOnly
record ReservationCancelRequest(
        @NotNull ReservationStatus status) {
}
