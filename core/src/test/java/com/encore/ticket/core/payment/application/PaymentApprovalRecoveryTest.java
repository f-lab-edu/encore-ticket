package com.encore.ticket.core.payment.application;

import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;
import com.encore.ticket.core.payment.domain.Payment;
import com.encore.ticket.core.payment.dto.PaymentStatus;
import com.encore.ticket.core.payment.exception.PaymentGatewayException;
import com.encore.ticket.core.payment.port.PaymentApproval;
import com.encore.ticket.core.payment.port.PaymentGateway;
import com.encore.ticket.core.payment.port.PaymentRefundRepository;
import com.encore.ticket.core.payment.port.PaymentRepository;
import com.encore.ticket.core.payment.port.PaymentSettlementCommand;
import com.encore.ticket.core.payment.port.PaymentSettlementResult;
import com.encore.ticket.core.payment.port.PaymentStartResult;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentApprovalRecoveryTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-08-04T10:00:00Z");
    private static final String KEY = "payment-key";
    private static final String ORDER = "reservation-501-1";
    private static final long AMOUNT = 30_000L;
    private static final long MEMBER = 100L;
    private static final long RESERVATION = 501L;

    @Mock PaymentRepository payments;
    @Mock PaymentRefundRepository refunds;
    @Mock ReservationRepository reservations;
    @Mock PaymentGateway gateway;

    private PaymentService service;
    private final Payment pending = Payment.builder()
            .id(700L).paymentKey(KEY).orderId(ORDER).amount(AMOUNT)
            .memberId(MEMBER).reservationId(RESERVATION).status(PaymentStatus.PENDING).build();

    @BeforeEach
    void setUp() {
        service = serviceAt(START.plusMinutes(1));
    }

    @ParameterizedTest
    @EnumSource(Entry.class)
    void GET_POST_스케줄러는_조회_후_같은_결제로_승인을_재시도한다(Entry entry) {
        prepare(entry);
        given(gateway.query(KEY)).willReturn(awaiting());
        given(reservations.findById(RESERVATION)).willReturn(Optional.of(reservation()));
        given(gateway.approve(KEY, ORDER, AMOUNT)).willReturn(approved());
        given(payments.settle(any())).willReturn(
                PaymentSettlementResult.confirmed(pending.complete("CARD", START.plusMinutes(1))));

        invoke(entry);

        var calls = inOrder(gateway, payments);
        calls.verify(gateway).query(KEY);
        calls.verify(gateway).approve(KEY, ORDER, AMOUNT);
        calls.verify(payments).settle(new PaymentSettlementCommand(
                KEY, ORDER, AMOUNT, "CARD", START.plusMinutes(1)));
        verify(gateway, times(1)).approve(KEY, ORDER, AMOUNT);
    }

    @ParameterizedTest
    @CsvSource({"599, true", "600, false", "601, false"})
    void 결제_시작_후_10분_경계에서_재승인_여부를_판단한다(int seconds, boolean retry) {
        service = serviceAt(START.plusSeconds(seconds));
        prepare(Entry.GET);
        given(gateway.query(KEY)).willReturn(awaiting());
        given(reservations.findById(RESERVATION)).willReturn(Optional.of(reservation()));
        if (retry) {
            given(gateway.approve(KEY, ORDER, AMOUNT)).willReturn(awaiting());
        }

        assertThat(service.result(ORDER, MEMBER).paymentStatus()).isEqualTo(PaymentStatus.PENDING);

        verify(gateway, times(retry ? 1 : 0)).approve(KEY, ORDER, AMOUNT);
        verify(payments, never()).decline(any(), any(), any());
        verify(payments, never()).settle(any());
        // 서비스는 좌석 보호 해제를 요청하지 않는다. 실제 DB 상태는 저장 모듈 검증 대상이다.
        verify(reservations, never()).expireBatch(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void PG_조회_중_10분이_지나면_승인을_재시도하지_않는다() {
        Clock clock = mock(Clock.class);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
        service = new PaymentService(payments, refunds, reservations, gateway, clock);
        prepare(Entry.GET);
        given(gateway.query(KEY)).willAnswer(invocation -> {
            given(clock.instant()).willReturn(START.plusSeconds(620).toInstant());
            return awaiting();
        });
        given(reservations.findById(RESERVATION)).willReturn(Optional.of(reservation()));

        service.result(ORDER, MEMBER);

        var calls = inOrder(gateway, clock);
        calls.verify(gateway).query(KEY);
        calls.verify(clock).instant();
        verify(gateway, never()).approve(any(), any(), any());
    }

    @Test
    void READY는_유예시간_안이어도_승인하지_않는다() {
        prepare(Entry.GET);
        given(gateway.query(KEY)).willReturn(PaymentApproval.pending(KEY, ORDER, AMOUNT, "READY"));

        assertThat(service.result(ORDER, MEMBER).paymentStatus()).isEqualTo(PaymentStatus.PENDING);

        verify(gateway, never()).approve(any(), any(), any());
    }

    @Test
    void 승인_대기_조회라도_금액이_다르면_승인하지_않는다() {
        prepare(Entry.GET);
        given(gateway.query(KEY)).willReturn(PaymentApproval.awaitingApproval(KEY, ORDER, AMOUNT + 1));

        assertThat(service.result(ORDER, MEMBER).paymentStatus()).isEqualTo(PaymentStatus.PENDING);

        verify(gateway, never()).approve(any(), any(), any());
        verify(payments, never()).decline(any(), any(), any());
    }

    @Test
    void 조회_실패는_재승인이나_결제_실패로_단정하지_않는다() {
        prepare(Entry.GET);
        given(gateway.query(KEY)).willThrow(new PaymentGatewayException("query timeout"));

        assertThat(service.result(ORDER, MEMBER).paymentStatus()).isEqualTo(PaymentStatus.PENDING);

        verify(gateway, never()).approve(any(), any(), any());
        verify(payments, never()).decline(any(), any(), any());
    }

    @Test
    void 재승인_타임아웃은_PENDING으로_남긴다() {
        prepare(Entry.GET);
        given(gateway.query(KEY)).willReturn(awaiting());
        given(reservations.findById(RESERVATION)).willReturn(Optional.of(reservation()));
        given(gateway.approve(KEY, ORDER, AMOUNT)).willThrow(new PaymentGatewayException("timeout"));

        assertThat(service.result(ORDER, MEMBER).paymentStatus()).isEqualTo(PaymentStatus.PENDING);

        verify(gateway, times(1)).approve(KEY, ORDER, AMOUNT);
        verify(payments, never()).decline(any(), any(), any());
    }

    @Test
    void 유예시간_10분이_지나도_DONE이면_재승인_없이_정산한다() {
        service = serviceAt(START.plusMinutes(11));
        prepare(Entry.GET);
        given(gateway.query(KEY)).willReturn(approved());
        given(payments.settle(any())).willReturn(
                PaymentSettlementResult.confirmed(pending.complete("CARD", START.plusMinutes(1))));

        assertThat(service.result(ORDER, MEMBER).paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);

        verify(gateway, never()).approve(any(), any(), any());
        verify(payments).settle(any());
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"CONFIRMED", "CANCELLED", "EXPIRED"})
    void 이미_종료된_예매는_승인을_재시도하지_않는다(ReservationStatus status) {
        prepare(Entry.GET);
        given(gateway.query(KEY)).willReturn(awaiting());
        given(reservations.findById(RESERVATION))
                .willReturn(Optional.of(reservation().toBuilder().status(status).build()));

        service.result(ORDER, MEMBER);

        verify(gateway, never()).approve(any(), any(), any());
    }

    private PaymentService serviceAt(OffsetDateTime now) {
        return new PaymentService(payments, refunds, reservations, gateway,
                Clock.fixed(now.toInstant(), ZoneOffset.UTC));
    }

    @ParameterizedTest
    @EnumSource(InvalidContext.class)
    void 현재_시도의_유효한_시작_시각이_없으면_승인하지_않는다(InvalidContext context) {
        prepare(Entry.GET);
        given(gateway.query(KEY)).willReturn(awaiting());
        Optional<Reservation> stored = switch (context) {
            case MISSING_RESERVATION -> Optional.empty();
            case MISSING_START -> Optional.of(reservation().toBuilder().paymentStartsAt(null).build());
            case NEXT_ATTEMPT -> Optional.of(reservation().toBuilder().paymentAttemptNo(2).build());
            case FUTURE_START -> Optional.of(reservation().toBuilder().paymentStartsAt(START.plusMinutes(2)).build());
        };
        given(reservations.findById(RESERVATION)).willReturn(stored);

        service.result(ORDER, MEMBER);

        verify(gateway, never()).approve(any(), any(), any());
    }

    private void prepare(Entry entry) {
        switch (entry) {
            case GET -> given(payments.getByOrderId(ORDER)).willReturn(pending);
            case POST -> given(payments.start(any())).willReturn(PaymentStartResult.replayed(pending));
            case SCHEDULER -> given(payments.findPendingForRecovery(START, 20)).willReturn(List.of(pending));
        }
    }

    private void invoke(Entry entry) {
        switch (entry) {
            case GET -> service.result(ORDER, MEMBER);
            case POST -> service.confirm(KEY, ORDER, AMOUNT, MEMBER);
            case SCHEDULER -> service.recoverPending(START, 20);
        }
    }

    private static Reservation reservation() {
        return Reservation.builder().id(RESERVATION).memberId(MEMBER)
                .status(ReservationStatus.PENDING_PAYMENT).paymentAttemptNo(1)
                .paymentStartsAt(START).expiresAt(START.plusSeconds(30)).build();
    }

    private static PaymentApproval awaiting() {
        return PaymentApproval.awaitingApproval(KEY, ORDER, AMOUNT);
    }

    private static PaymentApproval approved() {
        return PaymentApproval.approved(KEY, ORDER, AMOUNT, "CARD", START.plusMinutes(1));
    }

    private enum Entry { GET, POST, SCHEDULER }

    private enum InvalidContext { MISSING_RESERVATION, MISSING_START, NEXT_ATTEMPT, FUTURE_START }
}
