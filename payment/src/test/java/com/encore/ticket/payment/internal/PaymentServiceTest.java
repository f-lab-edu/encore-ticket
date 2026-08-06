package com.encore.ticket.payment.internal;

import com.encore.ticket.payment.api.ReservationCharge;
import com.encore.ticket.payment.api.dto.PaymentConfirmResponse;
import com.encore.ticket.payment.api.dto.PaymentResultResponse;
import com.encore.ticket.payment.api.dto.PaymentStatus;
import com.encore.ticket.payment.api.exception.AmountMismatchException;
import com.encore.ticket.payment.api.exception.CancelledReservationException;
import com.encore.ticket.payment.api.exception.ExpiredReservationException;
import com.encore.ticket.payment.api.exception.OrderIdAlreadyBoundException;
import com.encore.ticket.payment.api.exception.PaymentKeyReusedException;
import com.encore.ticket.payment.api.exception.ReservationNotOwnedException;
import com.encore.ticket.payment.api.exception.StalePaymentAttemptException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC);

    private static final String PAYMENT_KEY = "tgen_key";
    private static final String ORDER_ID = "reservation-501-1";
    private static final long AMOUNT = 330_000L;
    private static final long RESERVATION_ID = 501L;
    private static final long MEMBER_ID = 100L;
    private static final long OTHER_MEMBER_ID = 200L;
    private static final String HOLD_ID = "hold_7f32";

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentGateway paymentGateway;

    PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentRepository, paymentGateway, CLOCK);
    }

    private ReservationCharge charge() {
        return new ReservationCharge(
                RESERVATION_ID, MEMBER_ID, AMOUNT, ORDER_ID, HOLD_ID, false,
                OffsetDateTime.parse("2026-08-04T10:05:00Z"));
    }

    @Test
    void 처음_승인_요청하면_PG에_접수하고_PENDING_을_반환한다() {
        PaymentConfirmResponse response =
                service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID, charge());

        assertThat(response.paymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.approvedAt()).isNull();

        verify(paymentGateway).requestApproval(PAYMENT_KEY, ORDER_ID, AMOUNT);
        verify(paymentRepository).save(any());
    }

    @Test
    void 같은_요청을_다시_보내면_PG를_다시_부르지_않고_기존_결과를_반환한다() {
        Payment completed = new Payment(
                PAYMENT_KEY, ORDER_ID, AMOUNT, RESERVATION_ID, MEMBER_ID, HOLD_ID,
                PaymentStatus.COMPLETED, "CARD",
                OffsetDateTime.parse("2026-08-04T09:58:00Z"), null);
        given(paymentRepository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(completed));

        PaymentConfirmResponse response =
                service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID, charge());

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(response.method()).isEqualTo("CARD");
        assertThat(response.approvedAt()).isEqualTo(OffsetDateTime.parse("2026-08-04T09:58:00Z"));

        verify(paymentGateway, never()).requestApproval(any(), any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 같은_결제키를_다른_주문에_쓰면_실패한다() {
        Payment other = new Payment(
                PAYMENT_KEY, "reservation-999-1", AMOUNT, 999L, MEMBER_ID, HOLD_ID,
                PaymentStatus.PENDING, null, null, null);
        given(paymentRepository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(other));

        assertThatThrownBy(() -> service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID, charge()))
                .isInstanceOf(PaymentKeyReusedException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 같은_주문이_다른_결제키에_이미_묶였으면_실패한다() {
        Payment bound = new Payment(
                "tgen_other", ORDER_ID, AMOUNT, RESERVATION_ID, MEMBER_ID, HOLD_ID,
                PaymentStatus.PENDING, null, null, null);
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(bound));

        assertThatThrownBy(() -> service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID, charge()))
                .isInstanceOf(OrderIdAlreadyBoundException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 다른_사용자의_예매를_결제하면_실패한다() {
        assertThatThrownBy(() -> service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, OTHER_MEMBER_ID, charge()))
                .isInstanceOf(ReservationNotOwnedException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 취소된_예매를_결제하면_실패한다() {
        ReservationCharge cancelled = new ReservationCharge(
                RESERVATION_ID, MEMBER_ID, AMOUNT, ORDER_ID, HOLD_ID, true,
                OffsetDateTime.parse("2026-08-04T10:05:00Z"));

        assertThatThrownBy(() -> service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID, cancelled))
                .isInstanceOf(CancelledReservationException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 만료_시각에_도달한_예매를_결제하면_실패한다() {
        ReservationCharge expired = new ReservationCharge(
                RESERVATION_ID, MEMBER_ID, AMOUNT, ORDER_ID, HOLD_ID, false,
                OffsetDateTime.parse("2026-08-04T10:00:00Z"));

        assertThatThrownBy(() -> service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID, expired))
                .isInstanceOf(ExpiredReservationException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 오래된_주문으로_결제하면_실패한다() {
        ReservationCharge renewed = new ReservationCharge(
                RESERVATION_ID, MEMBER_ID, AMOUNT, "reservation-501-2", HOLD_ID, false,
                OffsetDateTime.parse("2026-08-04T10:05:00Z"));

        assertThatThrownBy(() -> service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID, renewed))
                .isInstanceOf(StalePaymentAttemptException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 요청_금액이_예매_금액과_다르면_실패한다() {
        assertThatThrownBy(() -> service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT + 1, MEMBER_ID, charge()))
                .isInstanceOf(AmountMismatchException.class);

        verify(paymentRepository, never()).save(any());
    }

    private void givenStored(PaymentStatus status, String method, OffsetDateTime approvedAt, String failReason) {
        given(paymentRepository.getByOrderId(ORDER_ID)).willReturn(new Payment(
                PAYMENT_KEY, ORDER_ID, AMOUNT, RESERVATION_ID, MEMBER_ID, HOLD_ID,
                status, method, approvedAt, failReason));
    }

    @Test
    void 처리_중인_결제_결과는_폴링_간격만_담는다() {
        givenStored(PaymentStatus.PENDING, null, null, null);

        PaymentResultResponse response = service.result(ORDER_ID, MEMBER_ID, null);

        assertThat(response.paymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.pollAfterSeconds()).isEqualTo(2);

        assertThat(response.reservationId()).isNull();
        assertThat(response.amount()).isNull();
        assertThat(response.method()).isNull();
        assertThat(response.reservationStatus()).isNull();
        assertThat(response.approvedAt()).isNull();
        assertThat(response.holdId()).isNull();
        assertThat(response.failReason()).isNull();
    }

    @Test
    void 완료된_결제_결과는_금액과_수단과_예매_상태를_담는다() {
        OffsetDateTime approvedAt = OffsetDateTime.parse("2026-08-04T09:58:00Z");
        givenStored(PaymentStatus.COMPLETED, "CARD", approvedAt, null);

        PaymentResultResponse response = service.result(ORDER_ID, MEMBER_ID, "CONFIRMED");

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(response.amount()).isEqualTo(AMOUNT);
        assertThat(response.method()).isEqualTo("CARD");
        assertThat(response.reservationStatus()).isEqualTo("CONFIRMED");
        assertThat(response.approvedAt()).isEqualTo(approvedAt);

        assertThat(response.pollAfterSeconds()).isNull();
        assertThat(response.holdId()).isNull();
        assertThat(response.failReason()).isNull();
    }

    @Test
    void 실패한_결제_결과는_실패_사유와_재결제용_선점_ID를_담는다() {
        givenStored(PaymentStatus.FAILED, null, null, "카드 한도 초과");

        PaymentResultResponse response = service.result(ORDER_ID, MEMBER_ID, "PENDING_PAYMENT");

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(response.holdId()).isEqualTo(HOLD_ID);
        assertThat(response.failReason()).isEqualTo("카드 한도 초과");

        assertThat(response.pollAfterSeconds()).isNull();
        assertThat(response.amount()).isNull();
        assertThat(response.method()).isNull();
        assertThat(response.reservationStatus()).isNull();
        assertThat(response.approvedAt()).isNull();
    }

    @Test
    void 선점으로_조회했을_때_결제_시도가_없으면_비어_있다() {
        given(paymentRepository.findLatestByHoldId(HOLD_ID)).willReturn(Optional.empty());

        assertThat(service.latestAttemptOf(HOLD_ID)).isEmpty();
    }

    @Test
    void 선점으로_조회하면_가장_최근_시도의_상태를_돌려준다() {
        given(paymentRepository.findLatestByHoldId(HOLD_ID)).willReturn(Optional.of(new Payment(
                PAYMENT_KEY, "reservation-501-2", AMOUNT, RESERVATION_ID, MEMBER_ID, HOLD_ID,
                PaymentStatus.FAILED, null, null, "카드 한도 초과")));

        assertThat(service.latestAttemptOf(HOLD_ID)).contains(PaymentStatus.FAILED);
    }

    @Test
    void 다른_사용자의_결제_결과를_조회하면_실패한다() {
        givenStored(PaymentStatus.COMPLETED, "CARD", OffsetDateTime.parse("2026-08-04T09:58:00Z"), null);

        assertThatThrownBy(() -> service.result(ORDER_ID, OTHER_MEMBER_ID, "CONFIRMED"))
                .isInstanceOf(ReservationNotOwnedException.class);
    }
}
