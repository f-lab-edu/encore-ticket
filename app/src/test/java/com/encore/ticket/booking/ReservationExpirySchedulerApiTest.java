package com.encore.ticket.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.encore.ticket.ApiSpecTestSupport;
import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.booking.reservation.domain.HeldSeats;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;
import com.encore.ticket.core.booking.seat.port.SeatAssignmentReader;

@TestPropertySource(properties = {
        "ticket.reservation.expiry.scheduler-interval=20ms",
        "ticket.reservation.expiry.batch-size=10"
})
class ReservationExpirySchedulerApiTest extends ApiSpecTestSupport {

    private static final long SCHEDULE_ID = 9_192L;
    private static final long SEAT_ID = 91_921L;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    SeatAssignmentReader seatAssignmentReader;

    @Test
    void production_scheduler가_만료_상태를_저장하고_현재_좌석을_해제한다() throws InterruptedException {
        OffsetDateTime now = OffsetDateTime.now(clock);
        HeldSeats hold = new HeldSeats(
                "hold_scheduler_" + System.nanoTime(),
                SCHEDULE_ID,
                List.of(SEAT_ID),
                1L,
                now.plusMinutes(1));
        Reservation expired = Reservation.create(hold, 100_000L, now.plusDays(1), clock)
                .toBuilder()
                .expiresAt(now.minusSeconds(1))
                .build();
        Reservation saved = reservationRepository.saveIssued(expired);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Reservation reloaded = reservationRepository.getById(saved.id());
            if (reloaded.status() == ReservationStatus.EXPIRED) {
                assertThat(reloaded.seatIds()).containsExactly(SEAT_ID);
                assertThat(seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID)).isEmpty();
                return;
            }
            Thread.sleep(20);
        }

        fail("scheduler가 제한 시간 안에 예매 만료와 좌석 해제를 완료하지 못했습니다.");
    }
}
