package com.encore.ticket.storage.db.booking.reservation;

import java.io.Serializable;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationSeatId implements Serializable {

    private Long reservationId;
    private Long seatId;
}
