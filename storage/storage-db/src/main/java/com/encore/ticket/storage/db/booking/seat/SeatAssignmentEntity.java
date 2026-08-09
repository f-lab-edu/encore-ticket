package com.encore.ticket.storage.db.booking.seat;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "seat_assignment")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SeatAssignmentEntity implements Persistable<Long> {

    @Id
    private Long seatId;

    private Long reservationId;

    private Long scheduleId;

    @Override
    public Long getId() {
        return seatId;
    }

    @Override
    public boolean isNew() {
        return true;
    }

}
