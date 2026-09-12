package com.encore.ticket.storage.db.payment;

import com.encore.ticket.core.payment.domain.PaymentRefund;
import com.encore.ticket.core.payment.domain.PaymentRefundRecovery;
import com.encore.ticket.core.payment.domain.PaymentRefundAttention;
import com.encore.ticket.core.payment.dto.RefundRecoveryCategory;
import com.encore.ticket.core.payment.port.PaymentCancellation;
import com.encore.ticket.core.payment.port.PaymentRefundRepository;
import com.encore.ticket.storage.db.support.MySqlContainerConfig;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlContainerConfig.class)
@Sql(statements = "DELETE FROM payment_refund WHERE payment_id = 81101")
@Sql(statements = "DELETE FROM payment_refund WHERE payment_id = 81101",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class RefundAttentionTest {
    @Autowired PaymentRefundRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private final Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(PaymentRefundRepositoryImpl.class);
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        jdbc.update("""
                INSERT INTO payment_refund(payment_id,payment_key,idempotency_key,amount,status)
                VALUES (81101,'private-payment-key','private-idempotency-key',50000,'PENDING')
                """);
    }

    @AfterEach
    void detachLogs() {
        logger.detachAppender(logs);
        logs.stop();
    }

    private PaymentRefund current() {
        return repository.findByPaymentId(81101L).orElseThrow();
    }

    private PaymentRefundRecovery needsCorrection() {
        return new PaymentRefundRecovery(RefundRecoveryCategory.CORRECTION_OR_REVIEW_REQUIRED,
                "UNAUTHORIZED_KEY", 0, null, "민감한 원문을 로그에 넣지 않음");
    }

    @Test
    void 확인_필요_발생을_DB에_보존하고_반복_조회는_중복_로그를_남기지_않는다() {
        var first = repository.tryClaim(81101L).orElseThrow();
        repository.finishClaim(first, null, needsCorrection());
        PaymentRefund saved = current();
        var second = repository.tryClaim(81101L).orElseThrow();
        repository.finishClaim(second, null, needsCorrection());

        assertThat(saved.attention().reason())
                .isEqualTo(PaymentRefundAttention.Reason.CORRECTION_OR_REVIEW_REQUIRED);
        assertThat(saved.attention().since()).isNotNull();
        assertThat(current().attention()).isEqualTo(saved.attention());
        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.getFirst().getFormattedMessage())
                .contains("event=refund_attention_required", "reason=CORRECTION_OR_REVIEW_REQUIRED")
                .doesNotContain("private-payment-key", "private-idempotency-key", "민감한");
    }

    @Test
    void PG_완료를_반영하면_확인_필요를_해결하고_해결_로그를_남긴다() {
        repository.updateRecovery(current(), needsCorrection());
        var before = current().attention();
        var claim = repository.tryClaim(81101L).orElseThrow();
        repository.finishClaim(claim,
                PaymentCancellation.completed("private-payment-key", 50000L,
                        OffsetDateTime.parse("2026-09-08T00:00:00Z")), null);

        var attention = current().attention();
        assertThat(attention.reason()).isNull();
        assertThat(attention.since()).isEqualTo(before.since());
        assertThat(attention.resolvedAt()).isNotNull();
        assertThat(logs.list).hasSize(2);
        assertThat(logs.list.getLast().getFormattedMessage()).contains("event=refund_attention_resolved");
    }

    @Test
    void DB_롤백이면_확인_필요_기록과_발생_로그를_남기지_않는다() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            repository.updateRecovery(current(), needsCorrection());
            assertThat(logs.list).isEmpty();
            status.setRollbackOnly();
        });

        assertThat(current().attention()).isEqualTo(PaymentRefundAttention.none());
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 한도_소진_사유로_바뀌어도_최초_확인_필요_시각은_유지한다() {
        repository.updateRecovery(current(), needsCorrection());
        var first = current().attention();
        repository.updateRecovery(current(), new PaymentRefundRecovery(
                RefundRecoveryCategory.RESULT_CONFIRMATION_REQUIRED,
                "OLD_ERROR", 5, null, "재전송 한도 소진"));

        assertThat(current().attention().reason()).isEqualTo(PaymentRefundAttention.Reason.RETRY_LIMIT_REACHED);
        assertThat(current().attention().since()).isEqualTo(first.since());
        assertThat(logs.list).hasSize(2);
    }
}
