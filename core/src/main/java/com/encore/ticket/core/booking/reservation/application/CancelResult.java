package com.encore.ticket.core.booking.reservation.application;

import com.encore.ticket.core.booking.dto.ReservationCancelResponse;

public record CancelResult(
        ReservationCancelResponse response,
        boolean alreadyCancelled
) {
}
