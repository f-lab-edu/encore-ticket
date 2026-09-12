package com.encore.ticket.core.payment.application;

import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;
import com.encore.ticket.core.payment.domain.Payment;
import com.encore.ticket.core.payment.domain.PaymentRefund;
import com.encore.ticket.core.payment.domain.PaymentRefundRecovery;
import com.encore.ticket.core.payment.domain.RefundRetryPolicy;
import com.encore.ticket.core.payment.dto.PaymentRefundStatus;
import com.encore.ticket.core.payment.dto.RefundRecoveryCategory;
import com.encore.ticket.core.payment.port.PaymentRefundClaim;
import com.encore.ticket.core.payment.dto.PaymentConfirmResponse;
import com.encore.ticket.core.payment.dto.PaymentResultResponse;
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

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final System.Logger LOG = System.getLogger(PaymentService.class.getName());
    private static final int POLL_AFTER_SECONDS = 2;
    private static final Duration APPROVAL_RETRY_WINDOW = Duration.ofMinutes(10);

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    public PaymentConfirmResponse confirm(
            String paymentKey, String orderId, Long amount, Long memberId) {
        PaymentStartResult started = paymentRepository.start(
                new PaymentStartCommand(paymentKey, orderId, amount, memberId));
        Payment payment = started.payment();

        if (payment.isPending()) {
            payment = started.newlyStarted()
                    ? approve(payment)
                    : queryAndRecover(payment);
        }

        PaymentOutcome outcome = recoverRefundIfNeeded(payment);
        return toConfirmResponse(outcome);
    }

    public PaymentResultResponse result(String orderId, Long memberId) {
        Payment payment = paymentRepository.getByOrderId(orderId);
        if (!payment.isOwnedBy(memberId)) {
            throw new ReservationNotOwnedException();
        }

        if (payment.isPending()) {
            payment = queryAndRecover(payment);
        }

        return toResultResponse(recoverRefundIfNeeded(payment));
    }

    public int recoverPending(OffsetDateTime before, int batchSize) {
        List<Payment> pendingPayments = paymentRepository.findPendingForRecovery(before, batchSize);
        for (Payment payment : pendingPayments) {
            try {
                recoverRefundIfNeeded(queryAndRecover(payment));
            } catch (RuntimeException exception) {
                LOG.log(System.Logger.Level.ERROR,
                        "event=payment_recovery_item_failed paymentId=" + payment.id(), exception);
            }
        }
        return pendingPayments.size();
    }

    public int recoverRefunds(OffsetDateTime before, int batchSize) {
        List<PaymentRefund> unresolvedRefunds =
                paymentRefundRepository.findForResultRecovery(before, batchSize);
        for (PaymentRefund refund : unresolvedRefunds) {
            try {
                processRefund(refund);
            } catch (RuntimeException exception) {
                LOG.log(System.Logger.Level.ERROR,
                        "event=refund_recovery_item_failed refundId=" + refund.id(), exception);
            }
        }
        return unresolvedRefunds.size();
    }

    private Payment approve(Payment payment) {
        try {
            return reconcile(payment, paymentGateway.approve(
                    payment.paymentKey(), payment.orderId(), payment.amount()));
        } catch (PaymentGatewayException exception) {
            LOG.log(System.Logger.Level.WARNING,
                    "event=payment_approval_unresolved paymentId=" + payment.id(), exception);
            return payment;
        }
    }

    private Payment queryAndRecover(Payment payment) {
        try {
            PaymentApproval approval = paymentGateway.query(payment.paymentKey());
            validateProviderIdentity(payment, approval);
            if (approval.state() == PaymentApproval.State.AWAITING_APPROVAL
                    && canRetryApproval(payment)) {
                return approve(payment);
            }
            return reconcile(payment, approval);
        } catch (PaymentGatewayException exception) {
            LOG.log(System.Logger.Level.WARNING,
                    "event=payment_query_unresolved paymentId=" + payment.id(), exception);
            return payment;
        }
    }

    private boolean canRetryApproval(Payment payment) {
        Reservation reservation = reservationRepository.findById(payment.reservationId()).orElse(null);
        if (reservation == null
                || !reservation.isPendingPayment()
                || !reservation.currentOrderId().equals(payment.orderId())
                || reservation.paymentStartsAt() == null) {
            return false;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime startedAt = reservation.paymentStartsAt();
        return !now.isBefore(startedAt) && now.isBefore(startedAt.plus(APPROVAL_RETRY_WINDOW));
    }

    private Payment reconcile(Payment payment, PaymentApproval approval) {
        validateProviderIdentity(payment, approval);
        return switch (approval.state()) {
            case APPROVED -> settle(approval).payment();
            case DECLINED, CANCELED -> paymentRepository.decline(
                    payment.paymentKey(), payment.orderId(), failureReason(approval));
            case PENDING, AWAITING_APPROVAL -> payment;
        };
    }

    private PaymentSettlementResult settle(PaymentApproval approval) {
        return paymentRepository.settle(
                new PaymentSettlementCommand(
                        approval.paymentKey(),
                        approval.orderId(),
                        approval.amount(),
                        approval.method(),
                        approval.approvedAt()));
    }

    private PaymentOutcome recoverRefundIfNeeded(Payment payment) {
        if (!payment.isCompleted()) {
            return new PaymentOutcome(payment, null);
        }

        PaymentRefund refund = paymentRefundRepository.findByPaymentId(payment.id())
                .map(this::processRefund)
                .orElse(null);
        return new PaymentOutcome(payment, refund);
    }

    private PaymentRefund processRefund(PaymentRefund refund) {
        if (refund.status() == PaymentRefundStatus.COMPLETED) {
            return refund;
        }
        var claimed = paymentRefundRepository.tryClaim(refund.paymentId());
        if (claimed.isEmpty()) {
            return paymentRefundRepository.findByPaymentId(refund.paymentId()).orElse(refund);
        }
        PaymentRefundClaim claim = claimed.get();
        refund = claim.refund();
        try {
            PaymentCancellation observed = paymentGateway.queryCancellation(
                    refund.paymentKey(), refund.amount());
            if (observed.isCompleted()) {
                return paymentRefundRepository.finishClaim(claim, observed, null);
            }
            // 전송 전인 최초 요청만 시작한다. 미완료 조회만으로 재전송을 허용하지 않는다.
            if (observed.state() == PaymentCancellation.State.NOT_CANCELED
                    && refund.isPending() && claim.requestStartedAt() == null
                    && refund.recovery().retryCount() == 0
                    && paymentRefundRepository.markRequestStarted(claim)) {
                PaymentCancellation result = paymentGateway.cancel(refund.paymentKey(), refund.amount(),
                        refund.reason(), refund.idempotencyKey());
                if (result.isCompleted()) {
                    return paymentRefundRepository.finishClaim(claim, result, null);
                }
                return paymentRefundRepository.finishClaim(claim, null,
                        new PaymentRefundRecovery(result.recoveryCategory(), result.failureCode(),
                                refund.recovery().retryCount(),
                                result.recoveryCategory() == RefundRecoveryCategory.AUTOMATIC_RECOVERY_CANDIDATE
                                        ? RefundRetryPolicy.nextRetryAt(refund.recovery().retryCount(),
                                                OffsetDateTime.now(clock)).orElse(null) : null,
                                "환불 재실행 조건 확인 필요"));
            }
            return paymentRefundRepository.finishClaim(claim, null,
                    afterResultQuery(refund, "기존 환불 결과 확인 필요 · 새 요청 보류"));
        } catch (PaymentGatewayException exception) {
            LOG.log(System.Logger.Level.WARNING,
                    "event=refund_unresolved refundId=" + refund.id(), exception);
            return paymentRefundRepository.finishClaim(claim, null,
                    afterResultQuery(refund, "PG 결과 확인 불가"));
        } finally {
            paymentRefundRepository.releaseClaim(claim);
        }
    }

    private PaymentRefundRecovery afterResultQuery(PaymentRefund refund, String fallbackReason) {
        PaymentRefundRecovery previous = refund.recovery();
        // 결과 조회는 재전송 횟수도, 이전 전송 종료 시각 기준 예약도 바꾸지 않는다.
        return new PaymentRefundRecovery(previous.category() == null
                ? RefundRecoveryCategory.RESULT_CONFIRMATION_REQUIRED : previous.category(),
                previous.errorCode(), previous.retryCount(), previous.nextRetryAt(),
                previous.stopReason() == null ? fallbackReason : previous.stopReason());
    }

    private PaymentConfirmResponse toConfirmResponse(PaymentOutcome outcome) {
        Payment payment = outcome.payment();
        PaymentRefund refund = outcome.refund();
        Reservation reservation = reservationRepository.findById(payment.reservationId()).orElse(null);

        return new PaymentConfirmResponse(
                payment.paymentKey(),
                payment.orderId(),
                payment.status(),
                payment.isPending() ? null : payment.reservationId(),
                payment.isCompleted() ? payment.amount() : null,
                payment.method(),
                reservationStatus(payment, reservation),
                payment.approvedAt(),
                refund == null ? null : refund.status(),
                refund == null ? null : refund.completedAt(),
                refund == null ? null : refund.failureReason());
    }

    private PaymentResultResponse toResultResponse(PaymentOutcome outcome) {
        Payment payment = outcome.payment();
        PaymentRefund refund = outcome.refund();
        Reservation reservation = reservationRepository.findById(payment.reservationId()).orElse(null);
        boolean processing = payment.isPending() || refund != null && refund.isPending();

        return new PaymentResultResponse(
                payment.paymentKey(),
                payment.orderId(),
                payment.status(),
                processing ? POLL_AFTER_SECONDS : null,
                payment.isPending() ? null : payment.reservationId(),
                payment.isCompleted() ? payment.amount() : null,
                payment.method(),
                reservationStatus(payment, reservation),
                payment.approvedAt(),
                payment.isFailed() ? payment.holdId() : null,
                payment.failReason(),
                refund == null ? null : refund.status(),
                refund == null ? null : refund.completedAt(),
                refund == null ? null : refund.failureReason());
    }

    private static String reservationStatus(Payment payment, Reservation reservation) {
        if (payment.isPending() || reservation == null) {
            return null;
        }
        return reservation.status().name();
    }

    private static void validateProviderIdentity(Payment payment, PaymentApproval approval) {
        if (!payment.paymentKey().equals(approval.paymentKey())
                || !payment.orderId().equals(approval.orderId())
                || !payment.amount().equals(approval.amount())) {
            throw new PaymentGatewayException("PG 결제 정보가 저장된 결제 시도와 다릅니다");
        }
        if (approval.isApproved()
                && (approval.method() == null || approval.approvedAt() == null)) {
            throw new PaymentGatewayException("PG 승인 결과에 결제 수단 또는 승인 시각이 없습니다");
        }
    }

    private static String failureReason(PaymentApproval approval) {
        if (approval.failureMessage() != null && !approval.failureMessage().isBlank()) {
            return approval.failureMessage();
        }
        return approval.failureCode() == null ? approval.state().name() : approval.failureCode();
    }

    private static String cancellationFailure(PaymentCancellation cancellation) {
        if (cancellation.failureMessage() != null && !cancellation.failureMessage().isBlank()) {
            return cancellation.failureMessage();
        }
        return cancellation.failureCode() == null ? "환불 실패" : cancellation.failureCode();
    }

    private record PaymentOutcome(Payment payment, PaymentRefund refund) {
    }
}
