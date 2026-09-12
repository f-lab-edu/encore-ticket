package com.encore.ticket.payment;

import com.encore.ticket.core.payment.application.PaymentService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class PaymentRecoverySchedulerTest {
    @Test
    void 결제_복구가_실패해도_환불_결과_조회는_실행한다() {
        PaymentService service = mock(PaymentService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-08T10:00:00Z"), ZoneOffset.UTC);
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusSeconds(70);
        when(service.recoverPending(cutoff, 20)).thenThrow(new IllegalStateException("결제 DB 오류"));

        new PaymentRecoveryScheduler(service, clock, Duration.ofSeconds(70), 20).recover();

        verify(service).recoverRefunds(cutoff, 20);
    }
}
