package com.encore.ticket.booking.internal.reservation;

import com.encore.ticket.booking.api.dto.ReservationCreateResponse;

record CreateResult(ReservationCreateResponse response, boolean created) {
}
