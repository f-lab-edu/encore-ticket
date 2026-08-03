package com.encore.ticket.booking.internal.reservation;

import com.encore.ticket.booking.api.dto.ReservationCancelResponse;

record CancelResult(
        ReservationCancelResponse response,
        boolean alreadyCancelled
) {
}
