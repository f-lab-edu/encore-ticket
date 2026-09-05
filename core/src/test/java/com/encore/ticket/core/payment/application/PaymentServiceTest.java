package com.encore.ticket.core.payment.application;

import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;
import com.encore.ticket.core.payment.domain.Payment;
import com.encore.ticket.core.payment.domain.PaymentRefund;
import com.encore.ticket.core.payment.dto.PaymentConfirmResponse;
import com.encore.ticket.core.payment.dto.PaymentRefundStatus;
import com.encore.ticket.core.payment.dto.PaymentResultResponse;
import com.encore.ticket.core.payment.dto.PaymentStatus;
import com.encore.ticket.core.payment.exception.PaymentGatewayException;
import com.encore.ticket.core.payment.exception.ReservationNotOwnedException;
import com.encore.ticket.core.payment.port.PaymentApproval;
import com.encore.ticket.core.payment.port.PaymentCancellation;
import com.encore.ticket.core.payment.port.PaymentGateway;
import com.encore.ticket.core.payment.port.PaymentRefundRepository;
import com.encore.ticket.core.payment.port.PaymentRepository;
import com.encore.ticket.core.payment.port.PaymentSettlementCommand;
import com.encore.ticket.core.payment.port.PaymentSettlementResult;
import com.encore.ticket.core.payment.port.PaymentStartCommand;
import com.encore.ticket.core.payment.port.PaymentStartResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String PAYMENT_KEY = "tgen_key";
    private static final String ORDER_ID = "reservation-501-1";
    private static final long AMOUNT = 330_000L;
    private static final long RESERVATION_ID = 501L;
    private static final long MEMBER_ID = 100L;
    private static final String HOLD_ID = "hold_7f32";
    private static final OffsetDateTime APPROVED_AT =
            OffsetDateTime.parse("2026-08-04T09:58:00Z");

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentRefundRepository paymentRefundRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock PaymentGateway paymentGateway;

    PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(
                paymentRepository,
                paymentRefundRepository,
                reservationRepository,
                paymentGateway);
    }

    @Test
    void 새_결제는_PENDING을_저장한_뒤_PG를_승인하고_예매_확정으로_수렴한다() {
        Payment pending = payment(PaymentStatus.PENDING);
        Payment completed = completedPayment();
        given(paymentRepository.start(any(PaymentStartCommand.class)))
                .willReturn(PaymentStartResult.started(pending));
        given(paymentGateway.approve(PAYMENT_KEY, ORDER_ID, AMOUNT))
                .willReturn(approved());
        given(paymentRepository.settle(any(PaymentSettlementCommand.class)))
                .willReturn(PaymentSettlementResult.confirmed(completed));
        given(paymentRefundRepository.findByPaymentId(completed.id())).willReturn(Optional.empty());
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation(
                ReservationStatus.CONFIRMED)));

        PaymentConfirmResponse response =
                service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID);

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.reservationStatus()).isEqualTo("CONFIRMED");
        assertThat(response.refundStatus()).isNull();
        verify(paymentGateway).approve(PAYMENT_KEY, ORDER_ID, AMOUNT);
        verify(paymentGateway, never()).query(any());
    }

    @Test
    void 기존_PENDING_재요청은_승인을_반복하지_않고_PG를_조회해_복구한다() {
        Payment pending = payment(PaymentStatus.PENDING);
        Payment completed = completedPayment();
        given(paymentRepository.start(any(PaymentStartCommand.class)))
                .willReturn(PaymentStartResult.replayed(pending));
        given(paymentGateway.query(PAYMENT_KEY)).willReturn(approved());
        given(paymentRepository.settle(any(PaymentSettlementCommand.class)))
                .willReturn(PaymentSettlementResult.confirmed(completed));
        given(paymentRefundRepository.findByPaymentId(completed.id())).willReturn(Optional.empty());
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation(
                ReservationStatus.CONFIRMED)));

        PaymentConfirmResponse response =
                service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID);

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentGateway).query(PAYMENT_KEY);
        verify(paymentGateway, never()).approve(any(), any(), any());
    }

    @Test
    void PG_승인_결과가_불명확하면_실패로_바꾸지_않고_PENDING을_반환한다() {
        Payment pending = payment(PaymentStatus.PENDING);
        given(paymentRepository.start(any(PaymentStartCommand.class)))
                .willReturn(PaymentStartResult.started(pending));
        given(paymentGateway.approve(PAYMENT_KEY, ORDER_ID, AMOUNT))
                .willThrow(new PaymentGatewayException("timeout"));
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation(
                ReservationStatus.PENDING_PAYMENT)));

        PaymentConfirmResponse response =
                service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID);

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).decline(any(), any(), any());
        verify(paymentRepository, never()).settle(any());
    }

    @Test
    void PG_금액이_다르면_예매_확정이나_실패로_단정하지_않는다() {
        given(paymentRepository.getByOrderId(ORDER_ID)).willReturn(payment(PaymentStatus.PENDING));
        given(paymentGateway.query(PAYMENT_KEY)).willReturn(PaymentApproval.approved(
                PAYMENT_KEY, ORDER_ID, AMOUNT + 1, "CARD", APPROVED_AT));

        PaymentResultResponse result = service.result(ORDER_ID, MEMBER_ID);

        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).settle(any());
        verify(paymentRepository, never()).decline(any(), any(), any());
    }

    @Test
    void PG가_명확히_거절하면_PAYMENT를_FAILED로_기록한다() {
        Payment pending = payment(PaymentStatus.PENDING);
        Payment failed = pending.fail("카드 한도 초과");
        given(paymentRepository.start(any(PaymentStartCommand.class)))
                .willReturn(PaymentStartResult.started(pending));
        given(paymentGateway.approve(PAYMENT_KEY, ORDER_ID, AMOUNT))
                .willReturn(PaymentApproval.declined(
                        PAYMENT_KEY, ORDER_ID, AMOUNT, "REJECT_CARD_PAYMENT", "카드 한도 초과"));
        given(paymentRepository.decline(PAYMENT_KEY, ORDER_ID, "카드 한도 초과"))
                .willReturn(failed);
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation(
                ReservationStatus.PENDING_PAYMENT)));

        PaymentConfirmResponse response =
                service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID);

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository, never()).settle(any());
    }

    @Test
    void 이미_FAILED인_같은_요청은_PG를_호출하지_않고_기존_결과를_반환한다() {
        Payment failed = payment(PaymentStatus.PENDING).fail("카드 한도 초과");
        given(paymentRepository.start(any(PaymentStartCommand.class)))
                .willReturn(PaymentStartResult.replayed(failed));
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation(
                ReservationStatus.PENDING_PAYMENT)));

        PaymentConfirmResponse response =
                service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID);

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentGateway, never()).approve(any(), any(), any());
        verify(paymentGateway, never()).query(any());
    }

    @Test
    void PG는_승인했지만_예매를_확정할_수_없으면_환불_PENDING을_노출한다() {
        Payment pending = payment(PaymentStatus.PENDING);
        Payment completed = completedPayment();
        PaymentRefund refund = pendingRefund(completed);
        given(paymentRepository.start(any(PaymentStartCommand.class)))
                .willReturn(PaymentStartResult.started(pending));
        given(paymentGateway.approve(PAYMENT_KEY, ORDER_ID, AMOUNT)).willReturn(approved());
        given(paymentRepository.settle(any(PaymentSettlementCommand.class)))
                .willReturn(PaymentSettlementResult.refundRequired(completed, refund));
        given(paymentRefundRepository.findByPaymentId(completed.id()))
                .willReturn(Optional.of(refund));
        given(paymentGateway.cancel(PAYMENT_KEY, AMOUNT, refund.reason(), refund.idempotencyKey()))
                .willThrow(new PaymentGatewayException("timeout"));
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation(
                ReservationStatus.EXPIRED)));

        PaymentConfirmResponse response =
                service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID);

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.reservationStatus()).isEqualTo("EXPIRED");
        assertThat(response.refundStatus()).isEqualTo(PaymentRefundStatus.PENDING);
        verify(paymentRefundRepository, never()).fail(any(), any());
    }

    @Test
    void 자동_환불이_완료되면_승인_사실과_환불_완료를_함께_반환한다() {
        Payment completed = completedPayment();
        PaymentRefund pendingRefund = pendingRefund(completed);
        PaymentRefund completedRefund = pendingRefund.complete(APPROVED_AT.plusMinutes(1));
        given(paymentRepository.start(any(PaymentStartCommand.class)))
                .willReturn(PaymentStartResult.replayed(completed));
        given(paymentRefundRepository.findByPaymentId(completed.id()))
                .willReturn(Optional.of(pendingRefund));
        given(paymentGateway.cancel(
                PAYMENT_KEY, AMOUNT, pendingRefund.reason(), pendingRefund.idempotencyKey()))
                .willReturn(PaymentCancellation.completed(
                        PAYMENT_KEY, AMOUNT, completedRefund.completedAt()));
        given(paymentRefundRepository.complete(pendingRefund, completedRefund.completedAt()))
                .willReturn(completedRefund);
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation(
                ReservationStatus.CANCELLED)));

        PaymentConfirmResponse response =
                service.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID);

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.reservationStatus()).isEqualTo("CANCELLED");
        assertThat(response.refundStatus()).isEqualTo(PaymentRefundStatus.COMPLETED);
        assertThat(response.refundedAt()).isEqualTo(completedRefund.completedAt());
    }

    @Test
    void 결과_조회는_PENDING을_PG_조회로_즉시_복구한다() {
        Payment pending = payment(PaymentStatus.PENDING);
        Payment completed = completedPayment();
        given(paymentRepository.getByOrderId(ORDER_ID)).willReturn(pending);
        given(paymentGateway.query(PAYMENT_KEY)).willReturn(approved());
        given(paymentRepository.settle(any(PaymentSettlementCommand.class)))
                .willReturn(PaymentSettlementResult.confirmed(completed));
        given(paymentRefundRepository.findByPaymentId(completed.id())).willReturn(Optional.empty());
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation(
                ReservationStatus.CONFIRMED)));

        PaymentResultResponse response = service.result(ORDER_ID, MEMBER_ID);

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.reservationStatus()).isEqualTo("CONFIRMED");
        assertThat(response.pollAfterSeconds()).isNull();
    }

    @Test
    void 다른_사용자의_결제는_PG를_조회하기_전에_차단한다() {
        Payment pending = payment(PaymentStatus.PENDING);
        given(paymentRepository.getByOrderId(ORDER_ID)).willReturn(pending);

        assertThatThrownBy(() -> service.result(ORDER_ID, MEMBER_ID + 1))
                .isInstanceOf(ReservationNotOwnedException.class);

        verify(paymentGateway, never()).query(any());
    }

    @Test
    void 스케줄러_복구는_저장된_PENDING만_PG에_조회한다() {
        Payment pending = payment(PaymentStatus.PENDING);
        OffsetDateTime cutoff = OffsetDateTime.parse("2026-08-04T10:00:00Z");
        given(paymentRepository.findPendingForRecovery(cutoff, 20))
                .willReturn(List.of(pending));
        given(paymentGateway.query(PAYMENT_KEY)).willReturn(PaymentApproval.pending(
                PAYMENT_KEY, ORDER_ID, AMOUNT, "IN_PROGRESS"));

        int recovered = service.recoverPending(cutoff, 20);

        assertThat(recovered).isEqualTo(1);
        verify(paymentGateway).query(PAYMENT_KEY);
        verify(paymentRepository, never()).settle(any());
        verify(paymentRepository, never()).decline(any(), any(), any());
    }

    @Test
    void 한_결제의_DB_복구가_실패해도_다음_결제를_처리한다() {
        Payment pending = payment(PaymentStatus.PENDING);
        OffsetDateTime cutoff = APPROVED_AT;
        given(paymentRepository.findPendingForRecovery(cutoff, 20))
                .willReturn(List.of(pending, pending));
        given(paymentGateway.query(PAYMENT_KEY)).willReturn(approved());
        given(paymentRepository.settle(any()))
                .willThrow(new IllegalStateException("DB 복구 실패"))
                .willReturn(PaymentSettlementResult.confirmed(completedPayment()));

        assertThat(service.recoverPending(cutoff, 20)).isEqualTo(2);

        verify(paymentRepository, org.mockito.Mockito.times(2)).settle(any());
        verify(paymentRefundRepository).findByPaymentId(pending.id());
    }

    @Test
    void 한_환불의_DB_복구가_실패해도_다음_환불을_처리한다() {
        PaymentRefund refund = pendingRefund(completedPayment());
        given(paymentRefundRepository.findPendingForRecovery(APPROVED_AT, 20))
                .willReturn(List.of(refund, refund));
        given(paymentGateway.cancel(any(), any(), any(), any()))
                .willReturn(PaymentCancellation.completed(PAYMENT_KEY, AMOUNT, APPROVED_AT));
        given(paymentRefundRepository.complete(refund, APPROVED_AT))
                .willThrow(new IllegalStateException("DB 복구 실패"))
                .willReturn(refund);

        assertThat(service.recoverRefunds(APPROVED_AT, 20)).isEqualTo(2);

        verify(paymentRefundRepository, org.mockito.Mockito.times(2)).complete(refund, APPROVED_AT);
    }

    private static Payment payment(PaymentStatus status) {
        return Payment.builder()
                .id(700L)
                .paymentKey(PAYMENT_KEY)
                .orderId(ORDER_ID)
                .amount(AMOUNT)
                .reservationId(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .holdId(HOLD_ID)
                .status(status)
                .build();
    }

    private static Payment completedPayment() {
        return payment(PaymentStatus.PENDING).complete("CARD", APPROVED_AT);
    }

    private static PaymentRefund pendingRefund(Payment payment) {
        return PaymentRefund.builder()
                .id(900L)
                .paymentId(payment.id())
                .paymentKey(payment.paymentKey())
                .idempotencyKey("refund-" + payment.paymentKey())
                .amount(payment.amount())
                .status(PaymentRefundStatus.PENDING)
                .reason("예매 확정 불가 자동 환불")
                .build();
    }

    private static PaymentApproval approved() {
        return PaymentApproval.approved(
                PAYMENT_KEY, ORDER_ID, AMOUNT, "CARD", APPROVED_AT);
    }

    private static Reservation reservation(ReservationStatus status) {
        return Reservation.builder()
                .id(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .scheduleId(10L)
                .seatIds(List.of(1L))
                .holdId(HOLD_ID)
                .amount(AMOUNT)
                .status(status)
                .paymentAttemptNo(1)
                .build();
    }
}
