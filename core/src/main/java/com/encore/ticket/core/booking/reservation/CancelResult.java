package com.encore.ticket.core.booking.reservation;

import com.encore.ticket.core.booking.dto.ReservationCancelResponse;

record CancelResult(
        ReservationCancelResponse response,
        boolean alreadyCancelled
) {
}
