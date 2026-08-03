package com.encore.ticket.payment.internal;

import com.encore.ticket.payment.api.ReservationCharge;
import com.encore.ticket.payment.api.dto.PaymentConfirmResponse;
import com.encore.ticket.payment.api.exception.AmountMismatchException;
import com.encore.ticket.payment.api.exception.CancelledReservationException;
import com.encore.ticket.payment.api.exception.ExpiredReservationException;
import com.encore.ticket.payment.api.exception.OrderIdAlreadyBoundException;
import com.encore.ticket.payment.api.exception.PaymentKeyReusedException;
import com.encore.ticket.payment.api.exception.ReservationNotOwnedException;
import com.encore.ticket.payment.api.exception.StalePaymentAttemptException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    PaymentService(PaymentRepository paymentRepository, PaymentGateway paymentGateway, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.clock = clock;
    }

    PaymentConfirmResponse confirm(String paymentKey, String orderId, Long amount,
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

        Payment accepted = Payment.accept(paymentKey, orderId, amount, charge.reservationId());
        paymentRepository.save(accepted);
        paymentGateway.requestApproval(paymentKey, orderId, amount);

        return toResponse(accepted);
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
