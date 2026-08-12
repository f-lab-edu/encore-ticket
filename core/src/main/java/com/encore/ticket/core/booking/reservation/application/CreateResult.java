package com.encore.ticket.core.booking.reservation.application;

import com.encore.ticket.core.booking.dto.ReservationCreateResponse;

public record CreateResult(ReservationCreateResponse response, boolean created) {
}
