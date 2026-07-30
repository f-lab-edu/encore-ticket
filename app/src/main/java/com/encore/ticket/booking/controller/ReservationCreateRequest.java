package com.encore.ticket.booking.controller;

import jakarta.validation.constraints.NotBlank;

record ReservationCreateRequest(
        @NotBlank String holdId) {
}
