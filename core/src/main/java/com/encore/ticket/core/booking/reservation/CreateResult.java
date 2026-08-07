package com.encore.ticket.core.booking.reservation;

import com.encore.ticket.core.booking.dto.ReservationCreateResponse;

record CreateResult(ReservationCreateResponse response, boolean created) {
}
