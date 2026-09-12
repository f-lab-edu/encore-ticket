package com.encore.ticket.storage.db.payment;

import com.encore.ticket.core.payment.domain.PaymentRefund;
import com.encore.ticket.core.payment.dto.PaymentRefundStatus;
import com.encore.ticket.core.payment.port.PaymentRefundRepository;
import com.encore.ticket.storage.db.support.MySqlContainerConfig;
import java.time.OffsetDateTime;
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
        PaymentRefund pending = repository.findByPaymentId(81002L).orElseThrow();

        PaymentRefund completed = repository.complete(pending, COMPLETED_AT);
        PaymentRefund replay = repository.complete(completed, COMPLETED_AT.plusMinutes(1));

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
    void 실패_상태와_사유가_저장되고_완료로_변경할_수_없다() {
        insert(81010L, "payment-key-10", "refund-key-10", PaymentRefundStatus.PENDING,
                "reason", null, null, CREATED_AT);
        PaymentRefund pending = repository.findByPaymentId(81010L).orElseThrow();

        repository.fail(pending, "환불 거절");
        PaymentRefund failed = repository.findByPaymentId(81010L).orElseThrow();

        assertThat(failed.status()).isEqualTo(PaymentRefundStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo("환불 거절");
        assertThatThrownBy(() -> repository.complete(failed, COMPLETED_AT))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(repository.findByPaymentId(81010L).orElseThrow()).isEqualTo(failed);
        assertThat(repository.findPendingForRecovery(OffsetDateTime.now(), 20)).isEmpty();
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
        List<PaymentRefund> first = repository.findPendingForRecovery(before, 2);
        List<PaymentRefund> second = repository.findPendingForRecovery(before, 2);

        assertThat(first).extracting(PaymentRefund::paymentId).containsExactly(81004L, 81005L);
        assertThat(second).extracting(PaymentRefund::paymentId).containsExactly(81006L);
        assertThat(repository.findPendingForRecovery(before, 2)).isEmpty();
        assertThat(repository.findPendingForRecovery(OffsetDateTime.now().plusSeconds(1), 2))
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
