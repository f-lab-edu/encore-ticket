package com.encore.ticket.storage.db.booking.reservation;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservation_seat")
@IdClass(ReservationSeatId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ReservationSeatEntity {

    @Id
    private Long reservationId;

    @Id
    private Long seatId;

    private Long scheduleId;
}
