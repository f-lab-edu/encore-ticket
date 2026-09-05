package com.encore.ticket.storage.db.payment;

import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.payment.dto.PaymentRefundStatus;
import com.encore.ticket.core.payment.dto.PaymentStatus;
import com.encore.ticket.core.payment.domain.Payment;
import com.encore.ticket.core.payment.port.PaymentRepository;
import com.encore.ticket.core.payment.port.PaymentRefundRepository;
import com.encore.ticket.core.payment.port.PaymentSettlementCommand;
import com.encore.ticket.core.payment.port.PaymentSettlementResult;
import com.encore.ticket.core.payment.port.PaymentStartCommand;
import com.encore.ticket.core.payment.port.PaymentStartResult;
import com.encore.ticket.storage.db.support.MySqlContainerConfig;

import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlContainerConfig.class)
@Sql(statements = {
        "DELETE FROM payment_refund WHERE payment_id IN (SELECT id FROM payment WHERE reservation_id BETWEEN 98001 AND 98030)",
        "DELETE FROM payment WHERE reservation_id BETWEEN 98001 AND 98030",
        "DELETE FROM reservation WHERE id BETWEEN 98001 AND 98030"
})
@Sql(statements = {
        "DELETE FROM payment_refund WHERE payment_id IN (SELECT id FROM payment WHERE reservation_id BETWEEN 98001 AND 98030)",
        "DELETE FROM payment WHERE reservation_id BETWEEN 98001 AND 98030",
        "DELETE FROM reservation WHERE id BETWEEN 98001 AND 98030"
}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PaymentRepositoryTransactionTest {

    private static final long MEMBER_ID = 100L;
    private static final long AMOUNT = 330_000L;
    private static final OffsetDateTime APPROVED_AT =
            OffsetDateTime.parse("2026-09-04T10:00:00Z");

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 결제_시작은_PAYMENT_PENDING과_paymentStartsAt을_같은_트랜잭션에_저장한다() {
        long reservationId = 98001L;
        insertReservation(reservationId, ReservationStatus.PENDING_PAYMENT);

        PaymentStartResult result = paymentRepository.start(command(reservationId));

        assertThat(result.newlyStarted()).isTrue();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment WHERE reservation_id = ?", Long.class, reservationId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payment_starts_at IS NOT NULL FROM reservation WHERE id = ?",
                Boolean.class, reservationId)).isTrue();
    }

    @Test
    void 같은_결제_시작을_재요청하면_새_행을_만들지_않고_기존_PENDING을_반환한다() {
        long reservationId = 98002L;
        insertReservation(reservationId, ReservationStatus.PENDING_PAYMENT);
        PaymentStartCommand command = command(reservationId);
        paymentRepository.start(command);

        PaymentStartResult replay = paymentRepository.start(command);

        assertThat(replay.newlyStarted()).isFalse();
        assertThat(replay.payment().status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment WHERE reservation_id = ?", Long.class, reservationId))
                .isEqualTo(1L);
    }

    @Test
    void PG_승인과_현재_예매가_일치하면_PAYMENT와_RESERVATION을_함께_완료한다() {
        long reservationId = 98003L;
        insertReservation(reservationId, ReservationStatus.PENDING_PAYMENT);
        PaymentStartResult started = paymentRepository.start(command(reservationId));

        PaymentSettlementResult result = paymentRepository.settle(settlement(started));

        assertThat(result.payment().status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.refund()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM reservation WHERE id = ?", String.class, reservationId))
                .isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payment_starts_at IS NULL FROM reservation WHERE id = ?",
                Boolean.class, reservationId)).isTrue();
    }

    @Test
    void PG는_승인했지만_예매가_종료됐으면_PAYMENT_COMPLETED와_REFUND_PENDING을_함께_저장한다() {
        long reservationId = 98004L;
        insertReservation(reservationId, ReservationStatus.PENDING_PAYMENT);
        PaymentStartResult started = paymentRepository.start(command(reservationId));
        jdbcTemplate.update("UPDATE reservation SET status = 'EXPIRED' WHERE id = ?", reservationId);

        PaymentSettlementResult result = paymentRepository.settle(settlement(started));

        assertThat(result.payment().status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.refund()).isNotNull();
        assertThat(result.refund().status()).isEqualTo(PaymentRefundStatus.PENDING);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM reservation WHERE id = ?", String.class, reservationId))
                .isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payment_starts_at IS NULL FROM reservation WHERE id = ?",
                Boolean.class, reservationId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM payment_refund WHERE payment_id = ?",
                String.class, result.payment().id())).isEqualTo("PENDING");
    }

    @Test
    void Reservation이_없어도_승인된_PAYMENT를_REFUND_PENDING으로_보존한다() {
        long reservationId = 98006L;
        PaymentStartResult started = startPending(reservationId);
        jdbcTemplate.update("DELETE FROM reservation WHERE id = ?", reservationId);

        PaymentSettlementResult result = paymentRepository.settle(settlement(started));

        assertThat(result.payment().status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.refund()).isNotNull();
        assertThat(result.refund().status()).isEqualTo(PaymentRefundStatus.PENDING);
    }

    @Test
    void 같은_PAYMENT에_대한_replay와_settle은_동시에_실행되어도_각각_정상_수렴한다() throws Exception {
        long reservationId = 98007L;
        PaymentStartResult started = startPending(reservationId);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<PaymentStartResult> replay = executor.submit(
                    () -> paymentRepository.start(command(reservationId)));
            Future<PaymentSettlementResult> settle = executor.submit(
                    () -> paymentRepository.settle(settlement(started)));

            assertThat(replay.get()).extracting(PaymentStartResult::newlyStarted)
                    .isEqualTo(false);
            assertThat(settle.get().payment().status()).isEqualTo(PaymentStatus.COMPLETED);
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM reservation WHERE id = ?", String.class, reservationId))
                .isEqualTo("CONFIRMED");
    }

    @Test
    void PG가_거절하면_PAYMENT를_FAILED로_바꾸고_현재_시도의_paymentStartsAt만_비운다() {
        long reservationId = 98005L;
        insertReservation(reservationId, ReservationStatus.PENDING_PAYMENT);
        PaymentStartResult started = paymentRepository.start(command(reservationId));

        var failed = paymentRepository.decline(
                started.payment().paymentKey(), started.payment().orderId(), "카드 한도 초과");

        assertThat(failed.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.failReason()).isEqualTo("카드 한도 초과");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payment_starts_at IS NULL FROM reservation WHERE id = ?",
                Boolean.class, reservationId)).isTrue();
    }

    @Test
    void 결제_복구_배치는_같은_cutoff에서_다음_대상을_선택하고_cutoff가_전진하면_재시도한다() {
        PaymentStartResult first = startPending(98010L);
        PaymentStartResult second = startPending(98011L);
        PaymentStartResult third = startPending(98012L);
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(1);
        jdbcTemplate.update("UPDATE payment SET created_at = ? WHERE id IN (?, ?, ?)",
                cutoff.minusSeconds(10), first.payment().id(), second.payment().id(), third.payment().id());

        assertThat(paymentRepository.findPendingForRecovery(cutoff, 2))
                .extracting(Payment::id)
                .containsExactly(first.payment().id(), second.payment().id());
        assertThat(paymentRepository.findPendingForRecovery(cutoff, 2))
                .extracting(Payment::id)
                .containsExactly(third.payment().id());
        assertThat(paymentRepository.findPendingForRecovery(OffsetDateTime.now().plusSeconds(1), 2))
                .extracting(Payment::id)
                .containsExactly(first.payment().id(), second.payment().id());
    }

    @Test
    void 환불_복구_배치는_같은_cutoff에서_다음_대상을_선택하고_cutoff가_전진하면_재시도한다() {
        PaymentStartResult first = startExpiredAndSettle(98020L);
        PaymentStartResult second = startExpiredAndSettle(98021L);
        PaymentStartResult third = startExpiredAndSettle(98022L);
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(1);
        jdbcTemplate.update("UPDATE payment_refund SET created_at = ? WHERE payment_id IN (?, ?, ?)",
                cutoff.minusSeconds(10), first.payment().id(), second.payment().id(), third.payment().id());

        var refunds = paymentRefundRepository.findPendingForRecovery(cutoff, 2);
        assertThat(refunds).extracting(refund -> refund.paymentId())
                .containsExactly(first.payment().id(), second.payment().id());
        assertThat(paymentRefundRepository.findPendingForRecovery(cutoff, 2))
                .extracting(refund -> refund.paymentId())
                .containsExactly(third.payment().id());
        assertThat(paymentRefundRepository.findPendingForRecovery(OffsetDateTime.now().plusSeconds(1), 2))
                .extracting(refund -> refund.paymentId())
                .containsExactly(first.payment().id(), second.payment().id());
    }

    private PaymentStartCommand command(long reservationId) {
        return new PaymentStartCommand(
                "tgen_" + reservationId,
                "reservation-" + reservationId + "-1",
                AMOUNT,
                MEMBER_ID);
    }

    private PaymentSettlementCommand settlement(PaymentStartResult started) {
        return new PaymentSettlementCommand(
                started.payment().paymentKey(),
                started.payment().orderId(),
                started.payment().amount(),
                "CARD",
                APPROVED_AT);
    }

    @Autowired
    PaymentRefundRepository paymentRefundRepository;

    private PaymentStartResult startPending(long reservationId) {
        insertReservation(reservationId, ReservationStatus.PENDING_PAYMENT);
        return paymentRepository.start(command(reservationId));
    }

    private PaymentStartResult startExpiredAndSettle(long reservationId) {
        PaymentStartResult started = startPending(reservationId);
        jdbcTemplate.update("UPDATE reservation SET status = 'EXPIRED' WHERE id = ?", reservationId);
        PaymentSettlementResult result = paymentRepository.settle(settlement(started));
        assertThat(result.refund()).isNotNull();
        return started;
    }

    private void insertReservation(long id, ReservationStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO reservation (
                    id, member_id, schedule_id, hold_id, amount, status,
                    reserved_at, performance_starts_at, original_expires_at, expires_at,
                    payment_attempt_no, payment_starts_at, cancelled_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, NULL, NULL)
                """,
                id,
                MEMBER_ID,
                980L,
                "hold_" + id,
                AMOUNT,
                status.name(),
                now,
                now.plusDays(1),
                now.plusHours(1),
                now.plusHours(1));
    }
}
