package com.encore.ticket.core.payment.port;

import com.encore.ticket.core.payment.domain.Payment;
import com.encore.ticket.core.payment.domain.PaymentRefund;

public record PaymentSettlementResult(Payment payment, PaymentRefund refund) {

    public static PaymentSettlementResult confirmed(Payment payment) {
        return new PaymentSettlementResult(payment, null);
    }

    public static PaymentSettlementResult refundRequired(
            Payment payment, PaymentRefund refund) {
        return new PaymentSettlementResult(payment, refund);
    }

    public boolean requiresRefund() {
        return refund != null;
    }
}
