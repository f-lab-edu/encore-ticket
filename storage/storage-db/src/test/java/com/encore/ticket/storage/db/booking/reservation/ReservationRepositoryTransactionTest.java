package com.encore.ticket.storage.db.booking.reservation;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.hibernate.exception.ConstraintViolationException;
import org.mockito.BDDMockito;
import org.springframework.dao.DataIntegrityViolationException;
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
import com.encore.ticket.core.booking.exception.ReservationAlreadyExistsException;
import com.encore.ticket.core.booking.exception.SeatAlreadyHeldException;
import com.encore.ticket.core.booking.exception.HoldNotOwnedException;
import com.encore.ticket.core.booking.exception.HoldExpiredException;
import com.encore.ticket.core.booking.exception.ReservationCancelledException;
import com.encore.ticket.core.payment.domain.Payment;
import com.encore.ticket.core.payment.dto.PaymentStatus;
import com.encore.ticket.core.payment.port.PaymentRepository;
import com.encore.ticket.storage.db.booking.seat.SeatAssignmentJpaRepository;
import com.encore.ticket.storage.db.support.MySqlContainerConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@Import(MySqlContainerConfig.class)
@Sql(statements = {
        "DELETE FROM payment WHERE reservation_id IN (SELECT id FROM reservation WHERE schedule_id = 910)",
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
    PaymentRepository paymentRepository;

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

    @Test
    void 같은_선점으로_예매를_중복_생성하면_전용_충돌을_반환한다() {
        Reservation issued = reservationRepository.saveIssued(newReservation());
        Reservation duplicate = newReservationWithHold(issued.holdId());

        assertThatThrownBy(() -> reservationRepository.saveIssued(duplicate))
                .isInstanceOf(ReservationAlreadyExistsException.class);
        assertThat(reservationRepository.findByHoldId(issued.holdId()))
                .get().extracting(Reservation::id).isEqualTo(issued.id());
    }

    @Test
    void 다른_선점이_같은_좌석을_예매하면_좌석_충돌로_롤백된다() {
        reservationRepository.saveIssued(newReservation());
        Reservation conflicting = newReservationWithHold("hold_conflict_" + System.nanoTime());

        assertThatThrownBy(() -> reservationRepository.saveIssued(conflicting))
                .isInstanceOf(SeatAlreadyHeldException.class);
        assertThat(reservationRepository.findByHoldId(conflicting.holdId())).isEmpty();
    }

    @Test
    void 현재_주문의_결제가_실패했을_때만_다음_주문번호를_한_번_발급한다() {
        Reservation issued = reservationRepository.saveIssued(freshReservation());
        paymentRepository.save(Payment.builder()
                .paymentKey("key-" + issued.id())
                .orderId(issued.currentOrderId())
                .amount(issued.amount())
                .reservationId(issued.id())
                .memberId(issued.memberId())
                .holdId(issued.holdId())
                .status(PaymentStatus.FAILED)
                .build());

        Reservation next = reservationRepository.prepareNextPaymentAttempt(issued.holdId(), issued.memberId());
        Reservation stable = reservationRepository.prepareNextPaymentAttempt(issued.holdId(), issued.memberId());

        assertThat(next.paymentAttemptNo()).isEqualTo(2);
        assertThat(stable.paymentAttemptNo()).isEqualTo(2);
    }

    @Test
    void 다음_결제_준비는_예매_소유자만_수행할_수_있다() {
        Reservation issued = reservationRepository.saveIssued(freshReservation());

        assertThatThrownBy(() -> reservationRepository.prepareNextPaymentAttempt(issued.holdId(), MEMBER_ID + 1))
                .isInstanceOf(HoldNotOwnedException.class);
    }

    @Test
    void 동시에_재요청해도_잠금으로_같은_다음_주문번호를_반환한다() throws Exception {
        Reservation issued = reservationRepository.saveIssued(freshReservation());
        paymentRepository.save(Payment.builder()
                .paymentKey("key-concurrent-" + issued.id())
                .orderId(issued.currentOrderId())
                .amount(issued.amount())
                .reservationId(issued.id())
                .memberId(issued.memberId())
                .holdId(issued.holdId())
                .status(PaymentStatus.FAILED)
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Reservation> first = executor.submit(() ->
                    reservationRepository.prepareNextPaymentAttempt(issued.holdId(), issued.memberId()));
            Future<Reservation> second = executor.submit(() ->
                    reservationRepository.prepareNextPaymentAttempt(issued.holdId(), issued.memberId()));

            assertThat(first.get(10, TimeUnit.SECONDS).paymentAttemptNo()).isEqualTo(2);
            assertThat(second.get(10, TimeUnit.SECONDS).paymentAttemptNo()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 현재_결제가_없거나_완료면_주문번호를_증가시키지_않는다() {
        Reservation pending = reservationRepository.saveIssued(freshReservation());
        Reservation unchangedPending = reservationRepository.prepareNextPaymentAttempt(pending.holdId(), pending.memberId());
        assertThat(unchangedPending.paymentAttemptNo()).isEqualTo(1);
        reservationRepository.saveCancelled(pending.cancel(Clock.systemUTC()));

        Reservation confirmed = reservationRepository.saveIssued(freshReservation())
                .toBuilder().status(ReservationStatus.CONFIRMED).build();
        reservationRepository.save(confirmed);
        Reservation unchangedConfirmed = reservationRepository.prepareNextPaymentAttempt(confirmed.holdId(), confirmed.memberId());
        assertThat(unchangedConfirmed.paymentAttemptNo()).isEqualTo(1);
    }

    @Test
    void 취소되거나_만료된_예매는_재결제를_준비할_수_없다() {
        Reservation issued = reservationRepository.saveIssued(freshReservation());
        Reservation cancelled = issued.cancel(Clock.systemUTC());
        Reservation persistedCancelled = reservationRepository.save(cancelled);
        assertThatThrownBy(() -> reservationRepository.prepareNextPaymentAttempt(cancelled.holdId(), cancelled.memberId()))
                .isInstanceOf(ReservationCancelledException.class);

        reservationRepository.saveCancelled(persistedCancelled);

        Reservation expired = reservationRepository.saveIssued(freshReservation())
                .toBuilder().expiresAt(OffsetDateTime.now().minusSeconds(1)).build();
        reservationRepository.save(expired);
        assertThatThrownBy(() -> reservationRepository.prepareNextPaymentAttempt(expired.holdId(), expired.memberId()))
                .isInstanceOf(HoldExpiredException.class);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "COMPLETED"})
    void 현재_주문이_진행_중이거나_결제_완료면_새_주문을_발급하지_않는다(PaymentStatus status) {
        Reservation issued = reservationRepository.saveIssued(freshReservation());
        paymentRepository.save(Payment.builder()
                .paymentKey("current-" + issued.id()).orderId(issued.currentOrderId())
                .amount(issued.amount()).reservationId(issued.id()).memberId(issued.memberId())
                .holdId(issued.holdId()).status(status).build());

        Reservation result = reservationRepository.prepareNextPaymentAttempt(issued.holdId(), issued.memberId());

        assertThat(result.currentOrderId()).isEqualTo(issued.currentOrderId());
        assertThat(reservationRepository.getById(issued.id()).paymentAttemptNo()).isEqualTo(1);
    }

    @Test
    void 필수_값_누락은_중복_예매나_좌석_충돌로_변환하지_않는다() {
        Reservation invalid = freshReservation().toBuilder().memberId(null).build();

        assertThatThrownBy(() -> reservationRepository.saveIssued(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(reservationRepository.findByHoldId(invalid.holdId())).isEmpty();
    }

    @Test
    void 다른_테이블의_PRIMARY_위반은_좌석_배정_충돌로_변환하지_않는다() {
        Reservation reservation = freshReservation();
        DataIntegrityViolationException failure = new DataIntegrityViolationException("injected unrelated key",
                new ConstraintViolationException("duplicate", new SQLException("duplicate", "23000", 1062),
                        "reservation_seat.PRIMARY"));
        BDDMockito.willThrow(failure).given(seatAssignmentJpa).saveAllAndFlush(any());

        assertThatThrownBy(() -> reservationRepository.saveIssued(reservation)).isSameAs(failure);
        assertThat(reservationRepository.findByHoldId(reservation.holdId())).isEmpty();
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

    private Reservation newReservationWithHold(String holdId) {
        HeldSeats hold = new HeldSeats(holdId, SCHEDULE_ID, SEAT_IDS, MEMBER_ID,
                OffsetDateTime.now(CLOCK).plusMinutes(7));
        return Reservation.create(hold, 300_000L, OffsetDateTime.now(CLOCK).plusDays(30), CLOCK);
    }

    private Reservation freshReservation() {
        Clock now = Clock.systemUTC();
        HeldSeats hold = new HeldSeats("hold_fresh_" + System.nanoTime(), SCHEDULE_ID, SEAT_IDS, MEMBER_ID,
                OffsetDateTime.now(now).plusMinutes(7));
        return Reservation.create(hold, 300_000L, OffsetDateTime.now(now).plusDays(30), now);
    }

    private static class SeatReleaseFailure extends RuntimeException {
    }
}
