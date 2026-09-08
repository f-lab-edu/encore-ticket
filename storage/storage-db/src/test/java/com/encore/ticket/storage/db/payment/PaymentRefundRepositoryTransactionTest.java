package com.encore.ticket.storage.db.payment;

import com.encore.ticket.core.payment.domain.PaymentRefund;
import com.encore.ticket.core.payment.domain.PaymentRefundRecovery;
import com.encore.ticket.core.payment.dto.RefundRecoveryCategory;
import com.encore.ticket.core.payment.dto.PaymentRefundStatus;
import com.encore.ticket.core.payment.port.PaymentRefundRepository;
import com.encore.ticket.core.payment.port.PaymentRefundClaim;
import com.encore.ticket.core.payment.port.PaymentCancellation;
import com.encore.ticket.storage.db.support.MySqlContainerConfig;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(MySqlContainerConfig.class)
@Sql(statements = "DELETE FROM payment_refund WHERE payment_id BETWEEN 81001 AND 81010")
@Sql(statements = "DELETE FROM payment_refund WHERE payment_id BETWEEN 81001 AND 81010",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PaymentRefundRepositoryTransactionTest {

    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-08-01T00:00:00Z");
    private static final OffsetDateTime COMPLETED_AT = OffsetDateTime.parse("2026-09-05T01:02:03Z");

    @Autowired
    PaymentRefundRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PaymentRefundJpaRepository jpa;

    @Test
    void 복구_정보를_DB에_저장한_뒤_새로_조회해도_유지한다() {
        PaymentRefundRecovery recovery = new PaymentRefundRecovery(
                RefundRecoveryCategory.CORRECTION_OR_REVIEW_REQUIRED,
                "UNAUTHORIZED_KEY", 2, null, "인증 설정 확인 필요");
        PaymentRefund refund = PaymentRefund.builder()
                .paymentId(81001L).paymentKey("payment-key-1").idempotencyKey("refund-key-1")
                .amount(12000L).status(PaymentRefundStatus.FAILED).reason("reason")
                .failureReason("기존 실패 사유").recovery(recovery).build();

        jpa.saveAndFlush(PaymentRefundMapper.toEntity(refund));
        PaymentRefund restored = repository.findByPaymentId(81001L).orElseThrow();

        assertThat(restored.recovery()).isEqualTo(recovery);
        assertThat(restored.failureReason()).isEqualTo("기존 실패 사유");
        assertThat(restored.status()).isEqualTo(PaymentRefundStatus.FAILED);
    }

    @Test
    void 다음_재시도_시각을_UTC로_복원한다() {
        PaymentRefundRecovery recovery = new PaymentRefundRecovery(
                RefundRecoveryCategory.AUTOMATIC_RECOVERY_CANDIDATE,
                "PROVIDER_ERROR", 1, COMPLETED_AT.plusMinutes(2), null);
        PaymentRefund refund = PaymentRefund.builder()
                .paymentId(81001L).paymentKey("payment-key-1").idempotencyKey("refund-key-1")
                .amount(12000L).status(PaymentRefundStatus.PENDING).recovery(recovery).build();

        jpa.saveAndFlush(PaymentRefundMapper.toEntity(refund));

        assertThat(repository.findByPaymentId(81001L).orElseThrow().recovery()).isEqualTo(recovery);
    }

    @Test
    void 기존_형식으로_생성한_환불은_복구_정보가_비어있고_횟수는_0이다() {
        insert(81001L, "payment-key-1", "refund-key-1", PaymentRefundStatus.FAILED,
                "reason", null, "기존 오류", CREATED_AT);

        assertThat(repository.findByPaymentId(81001L).orElseThrow().recovery())
                .isEqualTo(PaymentRefundRecovery.initial());
    }

    @Test
    void DB에서도_음수_재시도_횟수를_거절한다() {
        insert(81001L, "payment-key-1", "refund-key-1", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE payment_refund SET retry_count = -1 WHERE payment_id = 81001"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("ck_payment_refund_retry_count");
    }

    @Test
    void 복구_정보_갱신은_저장되며_낡은_정보로_덮어쓰지_않는다() {
        insert(81001L, "payment-key-1", "refund-key-1", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT);
        PaymentRefund before = repository.findByPaymentId(81001L).orElseThrow();
        PaymentRefundRecovery recovery = new PaymentRefundRecovery(
                RefundRecoveryCategory.AUTOMATIC_RECOVERY_CANDIDATE,
                "PROVIDER_ERROR", 1, COMPLETED_AT.plusMinutes(2), null);

        repository.updateRecovery(before, recovery);
        repository.updateRecovery(before, PaymentRefundRecovery.initial());

        PaymentRefund restored = repository.findByPaymentId(81001L).orElseThrow();
        assertThat(restored.recovery()).isEqualTo(recovery);
        assertThat(restored.status()).isEqualTo(PaymentRefundStatus.PENDING);
        assertThat(restored.amount()).isEqualTo(before.amount());
        assertThat(restored.idempotencyKey()).isEqualTo(before.idempotencyKey());
    }

    @Test
    void 이미_완료된_환불에는_늦은_복구_정보를_반영하지_않는다() {
        insert(81001L, "payment-key-1", "refund-key-1", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT);
        PaymentRefund before = repository.findByPaymentId(81001L).orElseThrow();
        repository.finishClaim(repository.tryClaim(81001L).orElseThrow(),
                PaymentCancellation.completed("payment-key-1", 12000L, COMPLETED_AT), null);

        repository.updateRecovery(before, new PaymentRefundRecovery(
                RefundRecoveryCategory.RESULT_CONFIRMATION_REQUIRED,
                "HTTP_500", 1, COMPLETED_AT.plusMinutes(1), null));

        PaymentRefund restored = repository.findByPaymentId(81001L).orElseThrow();
        assertThat(restored.status()).isEqualTo(PaymentRefundStatus.COMPLETED);
        assertThat(restored.recovery()).isEqualTo(PaymentRefundRecovery.initial());
    }

    @Test
    void 환불_전체_필드를_도메인으로_복원한다() {
        insert(81001L, "payment-key-1", "refund-key-1", PaymentRefundStatus.PENDING,
                "expired reservation", null, null, CREATED_AT);

        PaymentRefund refund = repository.findByPaymentId(81001L).orElseThrow();

        assertThat(refund.paymentKey()).isEqualTo("payment-key-1");
        assertThat(refund.idempotencyKey()).isEqualTo("refund-key-1");
        assertThat(refund.amount()).isEqualTo(12000L);
        assertThat(refund.status()).isEqualTo(PaymentRefundStatus.PENDING);
        assertThat(refund.reason()).isEqualTo("expired reservation");
    }

    @Test
    void PENDING_환불을_완료하고_완료된_환불의_재처리는_멱등하다() {
        insert(81002L, "payment-key-2", "refund-key-2", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT);
        PaymentRefundClaim claim = repository.tryClaim(81002L).orElseThrow();

        PaymentRefund completed = repository.finishClaim(claim,
                PaymentCancellation.completed("payment-key-2", 12000L, COMPLETED_AT), null);
        PaymentRefund replay = repository.finishClaim(claim,
                PaymentCancellation.completed("payment-key-2", 12000L, COMPLETED_AT.plusMinutes(1)), null);

        assertThat(completed.status()).isEqualTo(PaymentRefundStatus.COMPLETED);
        assertThat(completed.completedAt()).isEqualTo(COMPLETED_AT);
        assertThat(replay).isEqualTo(completed);
    }

    @Test
    void 완료된_환불은_실패로_되돌리지_않는다() {
        insert(81003L, "payment-key-3", "refund-key-3", PaymentRefundStatus.COMPLETED,
                "reason", COMPLETED_AT, null, CREATED_AT);
        PaymentRefund completed = repository.findByPaymentId(81003L).orElseThrow();

        assertThat(repository.fail(completed, "late failure")).isEqualTo(completed);
    }

    @Test
    void 실패_상태와_사유가_저장되고_완료_결과_없이는_완료되지_않는다() {
        insert(81010L, "payment-key-10", "refund-key-10", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT);
        PaymentRefund pending = repository.findByPaymentId(81010L).orElseThrow();

        repository.fail(pending, "환불 거절");
        PaymentRefund failed = repository.findByPaymentId(81010L).orElseThrow();

        assertThat(failed.status()).isEqualTo(PaymentRefundStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo("환불 거절");
        PaymentRefundClaim claim = repository.tryClaim(81010L).orElseThrow();
        repository.finishClaim(claim, PaymentCancellation.notCanceled("payment-key-10"), null);
        assertThat(repository.findByPaymentId(81010L).orElseThrow()).isEqualTo(failed);
        assertThat(repository.findForResultRecovery(OffsetDateTime.now(ZoneOffset.UTC), 20))
                .extracting(PaymentRefund::paymentId).containsExactly(81010L);
    }

    @Test
    void 복구_조회는_한_batch를_claim하고_다음_대상을_순환한다() {
        insert(81004L, "payment-key-4", "refund-key-4", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT);
        insert(81005L, "payment-key-5", "refund-key-5", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT.plusSeconds(1));
        insert(81006L, "payment-key-6", "refund-key-6", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT.plusSeconds(2));
        insert(81007L, "payment-key-7", "refund-key-7", PaymentRefundStatus.COMPLETED,
                "reason", COMPLETED_AT, null, CREATED_AT);

        OffsetDateTime before = OffsetDateTime.parse("2026-08-02T00:00:00Z");
        List<PaymentRefund> first = repository.findForResultRecovery(before, 2);
        List<PaymentRefund> second = repository.findForResultRecovery(before, 2);

        assertThat(first).extracting(PaymentRefund::paymentId).containsExactly(81004L, 81005L);
        assertThat(second).extracting(PaymentRefund::paymentId).containsExactly(81006L);
        assertThat(repository.findForResultRecovery(before, 2)).isEmpty();
        assertThat(repository.findForResultRecovery(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1), 2))
                .extracting(PaymentRefund::paymentId).containsExactly(81004L, 81005L);
    }

    @Test
    void payment_id와_멱등키는_각각_중복을_거부한다() {
        insert(81008L, "payment-key-8", "refund-key-8", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT);

        assertThatThrownBy(() -> insert(81008L, "payment-key-8b", "refund-key-8b", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(81009L, "payment-key-9", "refund-key-8", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 동시에_실행권을_요청해도_한_실행자만_획득한다() throws Exception {
        insert(81001L, "payment-key-1", "refund-key-1", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> acquire = () -> {
                ready.countDown();
                if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시 실행 대기 실패");
                }
                return repository.tryClaim(81001L).isPresent();
            };
            var first = executor.submit(acquire);
            var second = executor.submit(acquire);
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(5, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(5, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
        }
    }

    @Test
    void 만료된_실행자의_저장과_해제는_새_실행권을_침범하지_않는다() {
        insert(81001L, "payment-key-1", "refund-key-1", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT);
        PaymentRefundClaim old = repository.tryClaim(81001L).orElseThrow();
        jdbcTemplate.update("UPDATE payment_refund SET execution_until = ? WHERE payment_id = 81001", CREATED_AT);
        PaymentRefundClaim current = repository.tryClaim(81001L).orElseThrow();

        repository.finishClaim(old, PaymentCancellation.completed("payment-key-1", 12000L, COMPLETED_AT), null);
        repository.releaseClaim(old);

        assertThat(repository.findByPaymentId(81001L).orElseThrow().status())
                .isEqualTo(PaymentRefundStatus.PENDING);
        assertThat(repository.tryClaim(81001L)).isEmpty();
        repository.releaseClaim(current);
        assertThat(repository.tryClaim(81001L)).isPresent();
    }

    @Test
    void 환불_시작_기록은_실행권을_다시_얻어도_유지되고_중복_시작을_막는다() {
        insert(81001L, "payment-key-1", "refund-key-1", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT);
        PaymentRefundClaim first = repository.tryClaim(81001L).orElseThrow();
        assertThat(repository.markRequestStarted(first)).isTrue();
        repository.releaseClaim(first);
        PaymentRefundClaim second = repository.tryClaim(81001L).orElseThrow();

        assertThat(second.requestStartedAt()).isNotNull();
        assertThat(repository.markRequestStarted(second)).isFalse();
        assertThat(second.refund().idempotencyKey()).isEqualTo("refund-key-1");
    }

    @Test
    void FAILED여도_검증된_환불_완료는_실행권으로_반영한다() {
        insert(81001L, "payment-key-1", "refund-key-1", PaymentRefundStatus.FAILED,
                "reason", null, "이전 실패", CREATED_AT);
        PaymentRefundClaim claim = repository.tryClaim(81001L).orElseThrow();
        repository.finishClaim(claim,
                PaymentCancellation.completed("payment-key-1", 12000L, COMPLETED_AT), null);

        PaymentRefund restored = repository.findByPaymentId(81001L).orElseThrow();
        assertThat(restored.status()).isEqualTo(PaymentRefundStatus.COMPLETED);
        assertThat(restored.completedAt()).isEqualTo(COMPLETED_AT);
        assertThat(repository.tryClaim(81001L)).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("invalidCompletions")
    void 완료_결과의_식별자_금액_시각이_유효하지_않으면_DB를_완료하지_않는다(
            PaymentCancellation cancellation) {
        insert(81001L, "payment-key-1", "refund-key-1", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT);
        PaymentRefundClaim claim = repository.tryClaim(81001L).orElseThrow();
        assertThatThrownBy(() -> repository.finishClaim(claim, cancellation, null))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findByPaymentId(81001L).orElseThrow().status())
                .isEqualTo(PaymentRefundStatus.PENDING);
    }

    @Test
    void 재전송_예약과_한도와_별개로_미해결_환불의_결과를_조회한다() {
        insert(81001L, "payment-key-1", "refund-key-1", PaymentRefundStatus.FAILED,
                "reason", null, "이전 실패", CREATED_AT);
        insert(81002L, "payment-key-2", "refund-key-2", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT);
        jdbcTemplate.update("UPDATE payment_refund SET retry_count = 5, recovery_stop_reason = '한도 소진' WHERE payment_id = 81001");
        jdbcTemplate.update("UPDATE payment_refund SET next_retry_at = ? WHERE payment_id = 81002",
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));

        assertThat(repository.findForResultRecovery(OffsetDateTime.now(ZoneOffset.UTC), 20))
                .extracting(PaymentRefund::paymentId).containsExactly(81001L, 81002L);
        assertThat(repository.findByPaymentId(81001L).orElseThrow().recovery().retryCount()).isEqualTo(5);
    }

    @Test
    void 유효한_실행권이_있는_환불은_스케줄러가_선택하지_않는다() {
        insert(81001L, "payment-key-1", "refund-key-1", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT);
        repository.tryClaim(81001L).orElseThrow();

        assertThat(repository.findForResultRecovery(OffsetDateTime.now(ZoneOffset.UTC), 20)).isEmpty();
    }

    static List<PaymentCancellation> invalidCompletions() {
        return List.of(
                PaymentCancellation.completed("other-payment", 12000L, COMPLETED_AT),
                PaymentCancellation.completed("payment-key-1", 1L, COMPLETED_AT),
                PaymentCancellation.completed("payment-key-1", 12000L, null));
    }

    private void insert(long paymentId, String paymentKey, String idempotencyKey, PaymentRefundStatus status,
                        String reason, OffsetDateTime completedAt, String failureReason, OffsetDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO payment_refund
                    (payment_id, payment_key, idempotency_key, amount, status, reason,
                     completed_at, failure_reason, created_at, updated_at)
                VALUES (?, ?, ?, 12000, ?, ?, ?, ?, ?, ?)
                """, paymentId, paymentKey, idempotencyKey, status.name(), reason,
                completedAt, failureReason, createdAt, createdAt);
    }
}
