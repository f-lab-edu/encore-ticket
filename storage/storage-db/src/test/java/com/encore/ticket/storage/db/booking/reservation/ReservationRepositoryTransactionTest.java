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
import java.util.concurrent.CountDownLatch;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.booking.reservation.domain.HeldSeats;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;
import com.encore.ticket.core.booking.seat.port.SeatAssignmentReader;
import com.encore.ticket.core.booking.exception.ReservationAlreadyExistsException;
import com.encore.ticket.core.booking.exception.ReservationConcurrentModificationException;
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

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void 만료된_결제_대기_예매는_상태를_바꾸고_현재_좌석만_해제한다() {
        OffsetDateTime now = OffsetDateTime.now(CLOCK);
        Reservation issued = reservation(9101L, ReservationStatus.PENDING_PAYMENT, now.minusSeconds(1));
        Reservation saved = reservationRepository.saveIssued(issued);

        int expiredCount = reservationRepository.expireBatch(now, 10);

        Reservation reloaded = reservationRepository.getById(saved.id());
        assertThat(expiredCount).isEqualTo(1);
        assertThat(reloaded.status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reloaded.seatIds()).containsExactly(9101L);
        assertThat(seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID)).isEmpty();
    }

    @Test
    void 만료되지_않았거나_결제_대기_상태가_아니면_변경하지_않는다() {
        OffsetDateTime now = OffsetDateTime.now(CLOCK);
        Reservation future = reservationRepository.saveIssued(
                reservation(9101L, ReservationStatus.PENDING_PAYMENT, now.plusSeconds(1)));
        Reservation confirmed = reservationRepository.saveIssued(
                reservation(9102L, ReservationStatus.PENDING_PAYMENT, now.minusSeconds(2)));
        Reservation cancelled = reservationRepository.saveIssued(
                reservation(9103L, ReservationStatus.PENDING_PAYMENT, now.minusSeconds(3)));
        reservationRepository.save(confirmed.toBuilder().status(ReservationStatus.CONFIRMED).build());
        reservationRepository.save(cancelled.toBuilder().status(ReservationStatus.CANCELLED).build());

        int expiredCount = reservationRepository.expireBatch(now, 10);

        assertThat(expiredCount).isZero();
        assertThat(reservationRepository.getById(future.id()).status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservationRepository.getById(confirmed.id()).status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservationRepository.getById(cancelled.id()).status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID))
                .containsExactlyInAnyOrder(9101L, 9102L, 9103L);
    }

    @Test
    void 한_좌석_해제가_실패하면_같은_batch의_상태와_좌석을_모두_되돌린다() {
        OffsetDateTime now = OffsetDateTime.now(CLOCK);
        Reservation first = reservationRepository.saveIssued(
                reservation(9101L, ReservationStatus.PENDING_PAYMENT, now.minusSeconds(2)));
        Reservation second = reservationRepository.saveIssued(
                reservation(9102L, ReservationStatus.PENDING_PAYMENT, now.minusSeconds(1)));
        BDDMockito.willThrow(new SeatReleaseFailure())
                .given(seatAssignmentJpa).deleteByReservationId(any());

        assertThatThrownBy(() -> reservationRepository.expireBatch(now, 10))
                .isInstanceOf(SeatReleaseFailure.class);

        assertThat(reservationRepository.getById(first.id()).status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservationRepository.getById(second.id()).status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID))
                .containsExactlyInAnyOrder(9101L, 9102L);
    }

    @Test
    void 만료_전환과_경합한_낡은_취소는_만료_상태를_덮어쓰지_않는다() {
        OffsetDateTime now = OffsetDateTime.now(CLOCK);
        Reservation issued = reservationRepository.saveIssued(
                reservation(9101L, ReservationStatus.PENDING_PAYMENT, now.minusSeconds(1)));
        Reservation staleCancellation = reservationRepository.getById(issued.id()).cancel(CLOCK);
        reservationRepository.expireBatch(now, 10);

        assertThatThrownBy(() -> reservationRepository.saveCancelled(staleCancellation))
                .isInstanceOf(ReservationConcurrentModificationException.class);

        assertThat(reservationRepository.getById(issued.id()).status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID)).isEmpty();
    }

    @Test
    void 잠긴_만료_예매는_건너뛰고_다음_batch를_처리한다() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(CLOCK);
        Reservation first = reservationRepository.saveIssued(
                reservation(9101L, ReservationStatus.PENDING_PAYMENT, now.minusSeconds(4)));
        Reservation second = reservationRepository.saveIssued(
                reservation(9102L, ReservationStatus.PENDING_PAYMENT, now.minusSeconds(3)));
        Reservation third = reservationRepository.saveIssued(
                reservation(9103L, ReservationStatus.PENDING_PAYMENT, now.minusSeconds(2)));
        Reservation fourth = reservationRepository.saveIssued(
                reservation(9104L, ReservationStatus.PENDING_PAYMENT, now.minusSeconds(1)));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> lockOwner = executor.submit(() -> readCommittedTransaction().executeWithoutResult(status -> {
                jdbcTemplate.queryForList("""
                        SELECT id FROM reservation
                        WHERE status = 'PENDING_PAYMENT' AND expires_at <= ?
                        ORDER BY expires_at, id
                        LIMIT 2 FOR UPDATE
                        """, Long.class, now);
                locked.countDown();
                await(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            int expiredCount = reservationRepository.expireBatch(now, 2);

            assertThat(expiredCount).isEqualTo(2);
            assertThat(reservationRepository.getById(first.id()).status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
            assertThat(reservationRepository.getById(second.id()).status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
            assertThat(reservationRepository.getById(third.id()).status()).isEqualTo(ReservationStatus.EXPIRED);
            assertThat(reservationRepository.getById(fourth.id()).status()).isEqualTo(ReservationStatus.EXPIRED);

            release.countDown();
            lockOwner.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

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
    void 낡은_버전의_취소는_동시_변경_예외로_번역하고_좌석을_해제하지_않는다() {
        Reservation issued = reservationRepository.saveIssued(newReservation());
        Reservation stale = reservationRepository.getById(issued.id());
        reservationRepository.save(stale.startNextPaymentAttempt());

        assertThatThrownBy(() -> reservationRepository.saveCancelled(stale.cancel(CLOCK)))
                .isInstanceOf(ReservationConcurrentModificationException.class);

        Reservation reloaded = reservationRepository.getById(issued.id());
        assertThat(reloaded.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reloaded.paymentAttemptNo()).isEqualTo(2);
        assertThat(seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID))
                .containsExactlyInAnyOrderElementsOf(SEAT_IDS);
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

    private Reservation reservation(long seatId, ReservationStatus status, OffsetDateTime expiresAt) {
        HeldSeats hold = new HeldSeats(
                "hold_expiry_" + seatId + "_" + System.nanoTime(),
                SCHEDULE_ID,
                List.of(seatId),
                MEMBER_ID,
                OffsetDateTime.now(CLOCK).plusMinutes(7));
        return Reservation.create(hold, 150_000L, OffsetDateTime.now(CLOCK).plusDays(30), CLOCK)
                .toBuilder()
                .status(status)
                .expiresAt(expiresAt)
                .build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("잠금 해제 신호를 기다리는 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("잠금 대기 중 interrupt 되었습니다.", exception);
        }
    }

    private TransactionTemplate readCommittedTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }

    private static class SeatReleaseFailure extends RuntimeException {
    }
}
