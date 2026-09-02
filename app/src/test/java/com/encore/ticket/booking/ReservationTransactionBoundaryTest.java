package com.encore.ticket.booking;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import com.encore.ticket.core.booking.PaymentAttemptState;
import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.booking.hold.domain.SeatHold;
import com.encore.ticket.core.booking.hold.port.SeatHoldAcquireResult;
import com.encore.ticket.core.booking.hold.port.SeatHoldRepository;
import com.encore.ticket.core.booking.reservation.application.CreateResult;
import com.encore.ticket.core.booking.reservation.application.ReservationCreateService;
import com.encore.ticket.core.booking.reservation.application.ReservationService;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;
import com.encore.ticket.core.booking.seat.port.SeatAssignmentReader;
import com.encore.ticket.support.ContainersConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(ContainersConfig.class)
@Sql("/sql/reservation-boundary-fixture.sql")
class ReservationTransactionBoundaryTest {

    private static final long SCHEDULE_ID = 910L;
    private static final List<Long> CREATE_SEATS = List.of(9001L, 9002L);
    private static final List<Long> CONFLICT_SEATS = List.of(9003L, 9004L);
    private static final long TAKEN_SEAT = 9003L;
    private static final List<Long> CANCEL_SEATS = List.of(9005L, 9006L);
    private static final int NO_PURCHASE_LIMIT = 100;

    @Autowired
    ReservationCreateService reservationCreateService;

    @Autowired
    ReservationService reservationService;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    SeatAssignmentReader seatAssignmentReader;

    @Autowired
    SeatHoldRepository seatHoldRepository;

    @Autowired
    Clock clock;

    @Test
    void 예매가_생성되면_예매와_좌석_점유가_모두_남는다() {
        SeatHold hold = holdOf(1001L, CREATE_SEATS);

        CreateResult result = reservationCreateService.create(hold.holdId(), hold.memberId(), PaymentAttemptState.NONE);

        Reservation saved = reservationRepository.getById(result.response().reservationId());
        assertThat(saved.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(saved.seatIds()).containsExactlyInAnyOrderElementsOf(CREATE_SEATS);
        assertThat(seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID))
                .containsExactlyInAnyOrderElementsOf(CREATE_SEATS);
    }

    @Test
    @Sql(scripts = "/sql/reservation-boundary-fixture.sql",
            statements = "INSERT INTO seat_assignment (seat_id, reservation_id, schedule_id) VALUES (9003, 777, 910)")
    void 좌석_점유_생성이_실패하면_예매도_저장되지_않는다() {
        SeatHold hold = holdOf(1002L, CONFLICT_SEATS);

        assertThatThrownBy(() ->
                reservationCreateService.create(hold.holdId(), hold.memberId(), PaymentAttemptState.NONE))
                .isInstanceOf(RuntimeException.class);

        assertThat(reservationRepository.findByHoldId(hold.holdId())).isEmpty();
        assertThat(seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID)).containsExactly(TAKEN_SEAT);
    }

    @Test
    void 예매를_취소하면_예매는_CANCELLED_가_되고_좌석_점유는_사라진다() {
        SeatHold hold = holdOf(1003L, CANCEL_SEATS);
        CreateResult created = reservationCreateService.create(
                hold.holdId(), hold.memberId(), PaymentAttemptState.NONE);
        long reservationId = created.response().reservationId();

        reservationService.cancel(reservationId, hold.memberId());

        Optional<Reservation> cancelled = reservationRepository.findById(reservationId);
        assertThat(cancelled).isPresent();
        assertThat(cancelled.get().status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.get().cancelledAt()).isNotNull();

        Set<Long> assigned = seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID);
        assertThat(assigned).isEmpty();
    }

    private SeatHold holdOf(long memberId, List<Long> seatIds) {
        SeatHold hold = SeatHold.hold(SCHEDULE_ID, seatIds, memberId, clock);
        assertThat(seatHoldRepository.acquire(hold, NO_PURCHASE_LIMIT, "idem-" + hold.holdId(), "fp-" + hold.holdId()).result())
                .isEqualTo(SeatHoldAcquireResult.ACQUIRED);
        return hold;
    }
}
