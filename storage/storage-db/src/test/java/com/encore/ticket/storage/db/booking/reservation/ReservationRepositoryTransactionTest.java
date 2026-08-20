package com.encore.ticket.storage.db.booking.reservation;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;

import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.booking.reservation.domain.HeldSeats;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;
import com.encore.ticket.core.booking.seat.port.SeatAssignmentReader;
import com.encore.ticket.storage.db.booking.seat.SeatAssignmentJpaRepository;
import com.encore.ticket.storage.db.support.MySqlContainerConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(MySqlContainerConfig.class)
@Sql(statements = {
        "DELETE FROM seat_assignment WHERE schedule_id = 910",
        "DELETE FROM reservation_seat WHERE schedule_id = 910",
        "DELETE FROM reservation WHERE schedule_id = 910"
})
class ReservationRepositoryTransactionTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-20T10:00:00Z"), ZoneOffset.UTC);
    private static final long SCHEDULE_ID = 910L;
    private static final long MEMBER_ID = 100L;
    private static final List<Long> SEAT_IDS = List.of(9001L, 9002L);

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    SeatAssignmentReader seatAssignmentReader;

    @MockitoSpyBean
    SeatAssignmentJpaRepository seatAssignmentJpa;

    @Test
    void 좌석_해제가_실패하면_예매_상태도_취소_전으로_남는다() {
        Reservation issued = reservationRepository.saveIssued(newReservation());

        BDDMockito.willThrow(new SeatReleaseFailure())
                .given(seatAssignmentJpa).deleteByReservationId(issued.id());

        assertThatThrownBy(() -> reservationRepository.saveCancelled(issued.cancel(CLOCK)))
                .isInstanceOf(SeatReleaseFailure.class);

        Reservation reloaded = reservationRepository.getById(issued.id());
        assertThat(reloaded.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reloaded.cancelledAt()).isNull();

        Set<Long> assigned = seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID);
        assertThat(assigned).containsExactlyInAnyOrderElementsOf(SEAT_IDS);
    }

    @Test
    void 좌석_해제가_성공하면_예매는_취소되고_좌석_점유도_사라진다() {
        Reservation issued = reservationRepository.saveIssued(newReservation());

        reservationRepository.saveCancelled(issued.cancel(CLOCK));

        Reservation reloaded = reservationRepository.getById(issued.id());
        assertThat(reloaded.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID)).isEmpty();
    }

    private Reservation newReservation() {
        HeldSeats hold = new HeldSeats(
                "hold_" + System.nanoTime(),
                SCHEDULE_ID,
                SEAT_IDS,
                MEMBER_ID,
                OffsetDateTime.now(CLOCK).plusMinutes(7));

        return Reservation.create(hold, 300_000L, OffsetDateTime.now(CLOCK).plusDays(30), CLOCK);
    }

    private static class SeatReleaseFailure extends RuntimeException {
    }
}
