package com.encore.ticket.core.payment.application;

import com.encore.ticket.core.payment.domain.Payment;
import com.encore.ticket.core.payment.dto.PaymentStatus;
import com.encore.ticket.core.booking.CompletedPayment;
import com.encore.ticket.core.payment.port.PaymentRepository;

import java.util.Optional;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;

    public Optional<PaymentStatus> latestAttemptOf(String holdId) {
        return paymentRepository.findLatestByHoldId(holdId).map(Payment::status);
    }

    public CompletedPayment completedPaymentOf(Long reservationId) {
        return paymentRepository.findCompletedByReservationId(reservationId)
                .map(payment -> new CompletedPayment(payment.paymentKey(), payment.orderId()))
                .orElse(CompletedPayment.NONE);
    }
}
