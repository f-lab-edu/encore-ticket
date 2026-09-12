package com.encore.ticket.storage.db.payment;

import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.booking.exception.PaymentInProgressException;
import com.encore.ticket.core.exception.NotFoundException;
import com.encore.ticket.core.payment.domain.Payment;
import com.encore.ticket.core.payment.domain.PaymentRefund;
import com.encore.ticket.core.payment.domain.ReservationCharge;
import com.encore.ticket.core.payment.dto.PaymentStatus;
import com.encore.ticket.core.payment.exception.AmountMismatchException;
import com.encore.ticket.core.payment.exception.CancelledReservationException;
import com.encore.ticket.core.payment.exception.ExpiredReservationException;
import com.encore.ticket.core.payment.exception.OrderIdAlreadyBoundException;
import com.encore.ticket.core.payment.exception.PaymentKeyReusedException;
import com.encore.ticket.core.payment.exception.ReservationNotOwnedException;
import com.encore.ticket.core.payment.exception.StalePaymentAttemptException;
import com.encore.ticket.core.payment.port.PaymentRepository;
import com.encore.ticket.core.payment.port.PaymentSettlementCommand;
import com.encore.ticket.core.payment.port.PaymentSettlementResult;
import com.encore.ticket.core.payment.port.PaymentStartCommand;
import com.encore.ticket.core.payment.port.PaymentStartResult;
import com.encore.ticket.storage.db.booking.reservation.ReservationEntity;
import com.encore.ticket.storage.db.booking.reservation.ReservationJpaRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private static final String ORDER_ID_PREFIX = "reservation-";
    private static final String AUTOMATIC_REFUND_REASON = "예매 확정 불가 자동 환불";

    private final PaymentJpaRepository paymentJpa;
    private final PaymentRefundJpaRepository paymentRefundJpa;
    private final ReservationJpaRepository reservationJpa;
    private final Clock clock;

    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        return paymentJpa.findByOrderId(orderId).map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByPaymentKey(String paymentKey) {
        return paymentJpa.findByPaymentKey(paymentKey).map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findLatestByHoldId(String holdId) {
        return paymentJpa.findFirstByHoldIdOrderByIdDesc(holdId).map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findCompletedByReservationId(Long reservationId) {
        return paymentJpa.findFirstByReservationIdAndStatusOrderByIdDesc(
                        reservationId, PaymentStatus.COMPLETED)
                .map(PaymentMapper::toDomain);
    }

    @Override
    public void save(Payment payment) {
        paymentJpa.save(PaymentMapper.toEntity(payment));
    }

    @Override
    @Transactional
    public PaymentStartResult start(PaymentStartCommand command) {
        Long reservationId = parseReservationId(command.orderId());
        ReservationEntity reservation = reservationJpa.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException(
                        "존재하지 않는 예매입니다: " + reservationId));
        validateOwner(reservation, command.memberId());

        PaymentEntity paymentByKey = paymentJpa.findByPaymentKeyForUpdate(command.paymentKey())
                .orElse(null);
        PaymentEntity paymentByOrder = paymentJpa.findByOrderIdForUpdate(command.orderId())
                .orElse(null);

        if (paymentByKey != null && !paymentByKey.orderId().equals(command.orderId())) {
            throw new PaymentKeyReusedException();
        }
        if (paymentByOrder != null && !paymentByOrder.paymentKey().equals(command.paymentKey())) {
            throw new OrderIdAlreadyBoundException();
        }
        if (paymentByOrder != null) {
            validateReplay(paymentByOrder, command);
            return PaymentStartResult.replayed(PaymentMapper.toDomain(paymentByOrder));
        }

        validateNewAttempt(reservation, command);

        Payment pending = Payment.accept(
                command.paymentKey(),
                command.orderId(),
                command.amount(),
                new ReservationCharge(
                        reservation.id(),
                        reservation.memberId(),
                        reservation.amount(),
                        command.orderId(),
                        reservation.holdId(),
                        false,
                        reservation.expiresAt()));

        try {
            PaymentEntity saved = paymentJpa.saveAndFlush(PaymentMapper.toEntity(pending));
            reservation.startPayment(OffsetDateTime.now(clock));
            reservationJpa.flush();
            return PaymentStartResult.started(PaymentMapper.toDomain(saved));
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateConstraint(exception, "uk_payment_key")) {
                throw new PaymentKeyReusedException();
            }
            if (isDuplicateConstraint(exception, "uk_payment_order")) {
                throw new OrderIdAlreadyBoundException();
            }
            throw exception;
        }
    }

    @Override
    @Transactional
    public PaymentSettlementResult settle(PaymentSettlementCommand command) {
        Long reservationId = parseReservationId(command.orderId());
        ReservationEntity reservation = lockReservation(reservationId);
        PaymentEntity payment = paymentJpa.findByOrderIdForUpdate(command.orderId())
                .orElseThrow(StalePaymentAttemptException::new);
        validatePaymentReservation(payment, reservationId);
        validateSettlement(payment, command);

        if (payment.status() == PaymentStatus.PENDING) {
            payment.complete(command.method(), command.approvedAt());
            paymentJpa.flush();
        }

        if (canConfirm(reservation, command.orderId())) {
            reservation.confirmPayment();
            reservationJpa.flush();
            return PaymentSettlementResult.confirmed(PaymentMapper.toDomain(payment));
        }

        if (reservation != null && currentOrderId(reservation).equals(command.orderId())) {
            reservation.clearPaymentStart();
            reservationJpa.flush();
        }

        PaymentRefund refund = getOrCreateRefund(payment);
        return PaymentSettlementResult.refundRequired(PaymentMapper.toDomain(payment), refund);
    }

    @Override
    @Transactional
    public Payment decline(String paymentKey, String orderId, String reason) {
        Long reservationId = parseReservationId(orderId);
        ReservationEntity reservation = lockReservation(reservationId);
        PaymentEntity payment = paymentJpa.findByOrderIdForUpdate(orderId)
                .orElseThrow(StalePaymentAttemptException::new);
        validatePaymentReservation(payment, reservationId);
        if (!payment.paymentKey().equals(paymentKey)) {
            throw new PaymentKeyReusedException();
        }
        if (payment.status() == PaymentStatus.COMPLETED || payment.status() == PaymentStatus.FAILED) {
            return PaymentMapper.toDomain(payment);
        }

        payment.fail(reason);
        if (reservation != null && currentOrderId(reservation).equals(orderId)) {
            reservation.clearPaymentStart();
        }
        paymentJpa.flush();
        reservationJpa.flush();
        return PaymentMapper.toDomain(payment);
    }

    @Override
    @Transactional
    public List<Payment> findPendingForRecovery(OffsetDateTime before, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("결제 복구 batch 크기는 1 이상이어야 합니다: " + batchSize);
        }
        List<PaymentEntity> selected = paymentJpa.findPendingForRecovery(before, batchSize);
        OffsetDateTime claimedAt = OffsetDateTime.now(clock);
        selected.forEach(payment -> payment.markRecovery(claimedAt));
        paymentJpa.flush();
        return selected.stream().map(PaymentMapper::toDomain).toList();
    }

    private PaymentRefund getOrCreateRefund(PaymentEntity payment) {
        return paymentRefundJpa.findByPaymentId(payment.id())
                .map(PaymentRefundMapper::toDomain)
                .orElseGet(() -> {
                    PaymentRefund pending = PaymentRefund.pending(
                            PaymentMapper.toDomain(payment), AUTOMATIC_REFUND_REASON);
                    return PaymentRefundMapper.toDomain(
                            paymentRefundJpa.saveAndFlush(PaymentRefundMapper.toEntity(pending)));
                });
    }

    private static void validateOwner(ReservationEntity reservation, Long memberId) {
        if (!reservation.memberId().equals(memberId)) {
            throw new ReservationNotOwnedException();
        }
    }

    private static void validateReplay(PaymentEntity payment, PaymentStartCommand command) {
        if (!payment.amount().equals(command.amount())) {
            throw new PaymentKeyReusedException();
        }
        if (!payment.memberId().equals(command.memberId())) {
            throw new ReservationNotOwnedException();
        }
    }

    private void validateNewAttempt(ReservationEntity reservation, PaymentStartCommand command) {
        if (reservation.status() == ReservationStatus.CANCELLED) {
            throw new CancelledReservationException();
        }
        if (reservation.status() == ReservationStatus.EXPIRED
                || !OffsetDateTime.now(clock).isBefore(reservation.expiresAt())) {
            throw new ExpiredReservationException();
        }
        if (reservation.status() != ReservationStatus.PENDING_PAYMENT
                || !currentOrderId(reservation).equals(command.orderId())) {
            throw new StalePaymentAttemptException();
        }
        if (!reservation.amount().equals(command.amount())) {
            throw new AmountMismatchException();
        }
        if (reservation.paymentStartsAt() != null) {
            throw new PaymentInProgressException();
        }
    }

    private static void validateSettlement(
            PaymentEntity payment, PaymentSettlementCommand command) {
        if (!payment.paymentKey().equals(command.paymentKey())) {
            throw new PaymentKeyReusedException();
        }
        if (!payment.amount().equals(command.amount())) {
            throw new AmountMismatchException();
        }
        if (payment.status() == PaymentStatus.FAILED) {
            throw new StalePaymentAttemptException();
        }
    }

    private static boolean canConfirm(ReservationEntity reservation, String orderId) {
        return reservation != null
                && currentOrderId(reservation).equals(orderId)
                && (reservation.status() == ReservationStatus.PENDING_PAYMENT
                    || reservation.status() == ReservationStatus.CONFIRMED);
    }

    private ReservationEntity lockReservation(Long reservationId) {
        return reservationJpa.findByIdForUpdate(reservationId).orElse(null);
    }

    private static void validatePaymentReservation(
            PaymentEntity payment, Long reservationId) {
        if (!payment.reservationId().equals(reservationId)) {
            throw new StalePaymentAttemptException();
        }
    }

    private static String currentOrderId(ReservationEntity reservation) {
        return ORDER_ID_PREFIX + reservation.id() + "-" + reservation.paymentAttemptNo();
    }

    private static Long parseReservationId(String orderId) {
        try {
            if (!orderId.startsWith(ORDER_ID_PREFIX)) {
                throw new IllegalArgumentException();
            }
            int separator = orderId.lastIndexOf('-');
            if (separator <= ORDER_ID_PREFIX.length()) {
                throw new IllegalArgumentException();
            }
            return Long.valueOf(orderId.substring(ORDER_ID_PREFIX.length(), separator));
        } catch (RuntimeException exception) {
            throw new StalePaymentAttemptException();
        }
    }

    private static boolean isDuplicateConstraint(Throwable failure, String expectedName) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getSQLException().getErrorCode() == 1062
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase()
                            .endsWith(expectedName.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
