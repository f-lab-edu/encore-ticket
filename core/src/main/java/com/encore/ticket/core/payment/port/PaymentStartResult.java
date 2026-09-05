package com.encore.ticket.core.payment.port;

import com.encore.ticket.core.payment.domain.Payment;

public record PaymentStartResult(Payment payment, boolean newlyStarted) {

    public static PaymentStartResult started(Payment payment) {
        return new PaymentStartResult(payment, true);
    }

    public static PaymentStartResult replayed(Payment payment) {
        return new PaymentStartResult(payment, false);
    }
}
