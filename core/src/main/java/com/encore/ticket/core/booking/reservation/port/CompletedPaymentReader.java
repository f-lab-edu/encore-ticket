package com.encore.ticket.core.booking.reservation.port;

import com.encore.ticket.core.booking.CompletedPayment;

public interface CompletedPaymentReader {
    CompletedPayment completedPaymentOf(Long reservationId);
}
