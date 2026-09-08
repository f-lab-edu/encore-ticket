package com.encore.ticket.core.payment.application;

import com.encore.ticket.core.booking.reservation.port.ReservationRepository;
import com.encore.ticket.core.payment.domain.PaymentRefund;
import com.encore.ticket.core.payment.dto.PaymentRefundStatus;
import com.encore.ticket.core.payment.exception.PaymentGatewayException;
import com.encore.ticket.core.payment.port.*;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RefundExecutionTest {
    private final PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
    private final PaymentGateway pg = mock(PaymentGateway.class);
    private final PaymentService service = new PaymentService(mock(PaymentRepository.class), refunds,
            mock(ReservationRepository.class), pg, Clock.systemUTC());
    private final OffsetDateTime now = OffsetDateTime.parse("2026-09-08T00:00:00Z");
    private final PaymentRefund refund = PaymentRefund.builder().id(1L).paymentId(2L)
            .paymentKey("payment").idempotencyKey("refund-payment").amount(50000L)
            .reason("예매 확정 불가").status(PaymentRefundStatus.PENDING).build();

    private PaymentRefundClaim claim(OffsetDateTime started) {
        PaymentRefundClaim claim = new PaymentRefundClaim("owner", refund, started);
        when(refunds.findForResultRecovery(now, 10)).thenReturn(List.of(refund));
        when(refunds.tryClaim(2L)).thenReturn(Optional.of(claim));
        when(refunds.finishClaim(any(), any(), any())).thenReturn(refund);
        return claim;
    }

    @Test
    void 실행권이_없으면_PG에_접근하지_않는다() {
        when(refunds.findForResultRecovery(now, 10)).thenReturn(List.of(refund));
        when(refunds.tryClaim(2L)).thenReturn(Optional.empty());
        service.recoverRefunds(now, 10);
        verifyNoInteractions(pg);
    }

    @Test
    void 조회에서_완료를_확인하면_환불을_다시_보내지_않는다() {
        PaymentRefundClaim claim = claim(now);
        PaymentCancellation done = PaymentCancellation.completed("payment", 50000L, now);
        when(pg.queryCancellation("payment", 50000L)).thenReturn(done);
        service.recoverRefunds(now, 10);
        verify(refunds).finishClaim(claim, done, null);
        verify(pg, never()).cancel(any(), any(), any(), any());
        verify(refunds).releaseClaim(claim);
    }

    @Test
    void 최초_전송은_조회와_시작기록_커밋_다음에_기존_키로_실행한다() {
        PaymentRefundClaim claim = claim(null);
        when(pg.queryCancellation("payment", 50000L)).thenReturn(PaymentCancellation.notCanceled("payment"));
        when(refunds.markRequestStarted(claim)).thenReturn(true);
        when(pg.cancel(any(), any(), any(), any())).thenReturn(PaymentCancellation.completed("payment", 50000L, now));
        service.recoverRefunds(now, 10);
        var order = inOrder(pg, refunds);
        order.verify(pg).queryCancellation("payment", 50000L);
        order.verify(refunds).markRequestStarted(claim);
        order.verify(pg).cancel("payment", 50000L, "예매 확정 불가", "refund-payment");
    }

    @Test
    void 기존_요청은_미취소로_조회되어도_다시_전송하지_않는다() {
        claim(now);
        when(pg.queryCancellation("payment", 50000L)).thenReturn(PaymentCancellation.notCanceled("payment"));
        service.recoverRefunds(now, 10);
        verify(pg, never()).cancel(any(), any(), any(), any());
        verify(refunds, never()).markRequestStarted(any());
    }

    @Test
    void 조회_실패는_최초_환불도_전송하지_않는다() {
        PaymentRefundClaim claim = claim(null);
        when(pg.queryCancellation("payment", 50000L)).thenThrow(new PaymentGatewayException("timeout"));
        service.recoverRefunds(now, 10);
        verify(pg, never()).cancel(any(), any(), any(), any());
        verify(refunds).releaseClaim(claim);
    }

    @Test
    void 실행권이_만료되어_시작기록을_저장하지_못하면_전송하지_않는다() {
        claim(null);
        when(pg.queryCancellation("payment", 50000L)).thenReturn(PaymentCancellation.notCanceled("payment"));
        when(refunds.markRequestStarted(any())).thenReturn(false);
        service.recoverRefunds(now, 10);
        verify(pg, never()).cancel(any(), any(), any(), any());
    }
    @Test
    void 결과_조회는_재전송_횟수와_예약_시각을_변경하지_않는다() {
        var recovery = new com.encore.ticket.core.payment.domain.PaymentRefundRecovery(
                com.encore.ticket.core.payment.dto.RefundRecoveryCategory.AUTOMATIC_RECOVERY_CANDIDATE,
                "PROVIDER_ERROR", 4, now.plusMinutes(16), "재실행 보류");
        PaymentRefund existing = refund.toBuilder().recovery(recovery).build();
        PaymentRefundClaim claim = new PaymentRefundClaim("owner", existing, now);
        when(refunds.findForResultRecovery(now, 10)).thenReturn(List.of(existing));
        when(refunds.tryClaim(2L)).thenReturn(Optional.of(claim));
        when(pg.queryCancellation("payment", 50000L)).thenReturn(PaymentCancellation.notCanceled("payment"));

        service.recoverRefunds(now, 10);

        verify(refunds).finishClaim(claim, null, recovery);
        verify(pg, never()).cancel(any(), any(), any(), any());
    }

    @Test
    void 재전송_한도를_소진한_환불도_조회에서_완료되면_반영한다() {
        PaymentRefund existing = refund.toBuilder().status(PaymentRefundStatus.FAILED)
                .recovery(new com.encore.ticket.core.payment.domain.PaymentRefundRecovery(
                        com.encore.ticket.core.payment.dto.RefundRecoveryCategory.RESULT_CONFIRMATION_REQUIRED,
                        "OLD_ERROR", 5, null, "재전송 한도 소진")).build();
        PaymentRefundClaim claim = new PaymentRefundClaim("owner", existing, now);
        PaymentCancellation done = PaymentCancellation.completed("payment", 50000L, now);
        when(refunds.findForResultRecovery(now, 10)).thenReturn(List.of(existing));
        when(refunds.tryClaim(2L)).thenReturn(Optional.of(claim));
        when(pg.queryCancellation("payment", 50000L)).thenReturn(done);

        service.recoverRefunds(now, 10);

        verify(refunds).finishClaim(claim, done, null);
        verify(pg, never()).cancel(any(), any(), any(), any());
    }

    @Test
    void 최초_환불의_일시적_오류는_종료_시각에서_1분_뒤를_예약한다() {
        PaymentRefundClaim claim = claim(null);
        when(pg.queryCancellation("payment", 50000L)).thenReturn(PaymentCancellation.notCanceled("payment"));
        when(refunds.markRequestStarted(claim)).thenReturn(true);
        when(pg.cancel(any(), any(), any(), any())).thenReturn(PaymentCancellation.failed(
                "payment", "PROVIDER_ERROR", "일시적 오류",
                com.encore.ticket.core.payment.dto.RefundRecoveryCategory.AUTOMATIC_RECOVERY_CANDIDATE));
        PaymentService fixedService = new PaymentService(mock(PaymentRepository.class), refunds,
                mock(ReservationRepository.class), pg, Clock.fixed(now.toInstant(), java.time.ZoneOffset.UTC));

        fixedService.recoverRefunds(now, 10);

        verify(refunds).finishClaim(eq(claim), isNull(), argThat(recovery ->
                recovery.retryCount() == 0 && now.plusMinutes(1).equals(recovery.nextRetryAt())));
    }
}
