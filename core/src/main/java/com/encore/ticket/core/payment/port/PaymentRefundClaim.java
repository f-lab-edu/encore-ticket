package com.encore.ticket.core.payment.port;

import com.encore.ticket.core.payment.domain.PaymentRefund;
import java.time.OffsetDateTime;

public record PaymentRefundClaim(String token, PaymentRefund refund, OffsetDateTime requestStartedAt) {
}
