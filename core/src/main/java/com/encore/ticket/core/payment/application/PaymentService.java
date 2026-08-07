package com.encore.ticket.core.payment.application;

import com.encore.ticket.core.payment.domain.ReservationCharge;
import com.encore.ticket.core.payment.dto.PaymentConfirmResponse;
import com.encore.ticket.core.payment.dto.PaymentResultResponse;
import com.encore.ticket.core.payment.dto.PaymentStatus;
import com.encore.ticket.core.payment.exception.AmountMismatchException;
import com.encore.ticket.core.payment.exception.CancelledReservationException;
import com.encore.ticket.core.payment.exception.ExpiredReservationException;
import com.encore.ticket.core.payment.exception.OrderIdAlreadyBoundException;
import com.encore.ticket.core.payment.exception.PaymentKeyReusedException;
import com.encore.ticket.core.payment.exception.ReservationNotOwnedException;
import com.encore.ticket.core.payment.exception.StalePaymentAttemptException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import com.encore.ticket.core.payment.domain.Payment;
import com.encore.ticket.core.payment.port.PaymentGateway;
import com.encore.ticket.core.payment.port.PaymentRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PaymentService {

    private static final int POLL_AFTER_SECONDS = 2;

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    public PaymentConfirmResponse confirm(String paymentKey, String orderId, Long amount,
                                   Long memberId, ReservationCharge charge) {
        if (!charge.memberId().equals(memberId)) {
            throw new ReservationNotOwnedException();
        }

        Optional<Payment> byKey = paymentRepository.findByPaymentKey(paymentKey);
        if (byKey.isPresent()) {
            Payment existing = byKey.get();
            if (!existing.sameRequestAs(orderId, amount)) {
                throw new PaymentKeyReusedException();
            }
            return toResponse(existing);
        }

        if (paymentRepository.findByOrderId(orderId)
                .filter(bound -> bound.boundToOtherKey(paymentKey))
                .isPresent()) {
            throw new OrderIdAlreadyBoundException();
        }

        if (charge.cancelled()) {
            throw new CancelledReservationException();
        }
        if (!charge.expiresAt().isAfter(OffsetDateTime.now(clock))) {
            throw new ExpiredReservationException();
        }
        if (!charge.currentOrderId().equals(orderId)) {
            throw new StalePaymentAttemptException();
        }
        if (!charge.amount().equals(amount)) {
            throw new AmountMismatchException();
        }

        Payment accepted = Payment.accept(paymentKey, orderId, amount, charge);
        paymentRepository.save(accepted);
        paymentGateway.requestApproval(paymentKey, orderId, amount);

        return toResponse(accepted);
    }

    public Optional<PaymentStatus> latestAttemptOf(String holdId) {
        return paymentRepository.findLatestByHoldId(holdId).map(Payment::status);
    }

    public PaymentResultResponse result(String orderId, Long memberId, String reservationStatus) {
        Payment payment = paymentRepository.getByOrderId(orderId);
        if (!payment.isOwnedBy(memberId)) {
            throw new ReservationNotOwnedException();
        }

        if (payment.isPending()) {
            return new PaymentResultResponse(
                    payment.paymentKey(), payment.orderId(), payment.status(), POLL_AFTER_SECONDS,
                    null, null, null, null, null, null, null);
        }
        if (payment.isFailed()) {
            return new PaymentResultResponse(
                    payment.paymentKey(), payment.orderId(), payment.status(), null,
                    payment.reservationId(), null, null, null, null,
                    payment.holdId(), payment.failReason());
        }
        return new PaymentResultResponse(
                payment.paymentKey(), payment.orderId(), payment.status(), null,
                payment.reservationId(), payment.amount(), payment.method(), reservationStatus,
                payment.approvedAt(), null, null);
    }

    private PaymentConfirmResponse toResponse(Payment payment) {
        return new PaymentConfirmResponse(
                payment.paymentKey(),
                payment.orderId(),
                payment.status(),
                payment.reservationId(),
                payment.amount(),
                payment.method(),
                null,
                payment.approvedAt());
    }
}
