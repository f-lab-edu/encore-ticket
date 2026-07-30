package com.encore.ticket.payment.controller;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.encore.ticket.payment.api.dto.PaymentConfirmResponse;
import com.encore.ticket.payment.api.dto.PaymentResultResponse;
import com.encore.ticket.payment.api.dto.PaymentStatus;

final class StubPayments {

    static final String PAYMENT_KEY = "tgen_20260801200700AbCdE";

    static final String ACCEPTED_ORDER_ID = "reservation-501-1";

    static final String COMPLETED_ORDER_ID = "reservation-502-1";

    static final String OTHER_MEMBER_ORDER_ID = "reservation-503-1";

    static final String CANCELLED_ORDER_ID = "reservation-504-1";

    static final String EXPIRED_ORDER_ID = "reservation-505-1";

    static final String FAILED_ORDER_ID = "reservation-506-1";

    static final String MISSING_ORDER_ID = "reservation-999-1";

    static final long EXPECTED_AMOUNT = 330_000L;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private static final OffsetDateTime APPROVED_AT =
            OffsetDateTime.of(2026, 8, 1, 20, 8, 10, 0, KST);

    private static final int POLL_AFTER_SECONDS = 2;

    private static final String METHOD = "CARD";

    private static final String FAIL_REASON = "카드 한도 초과";

    private static final String FAILED_HOLD_ID = "hold_7f32";

    private static final Map<String, StubPayment> PAYMENTS = createPayments();

    private StubPayments() {
    }

    static boolean exists(String orderId) {
        return PAYMENTS.containsKey(orderId);
    }

    static boolean ownedByStubMember(String orderId) {
        return Optional.ofNullable(PAYMENTS.get(orderId))
                .map(StubPayment::owned)
                .orElse(false);
    }

    static boolean alreadyCompleted(String orderId) {
        return Optional.ofNullable(PAYMENTS.get(orderId))
                .map(payment -> payment.paymentStatus() == PaymentStatus.COMPLETED)
                .orElse(false);
    }

    static PaymentConfirmResponse confirm(String paymentKey, String orderId) {
        StubPayment payment = PAYMENTS.get(orderId);

        if (payment.paymentStatus() != PaymentStatus.COMPLETED) {
            return new PaymentConfirmResponse(
                    paymentKey, orderId, PaymentStatus.PENDING, null, null, null, null, null);
        }

        return new PaymentConfirmResponse(
                paymentKey,
                orderId,
                PaymentStatus.COMPLETED,
                payment.reservationId(),
                EXPECTED_AMOUNT,
                METHOD,
                payment.reservationStatus(),
                APPROVED_AT);
    }

    static Optional<PaymentResultResponse> result(String orderId) {
        return Optional.ofNullable(PAYMENTS.get(orderId)).map(StubPayments::toResult);
    }

    private static PaymentResultResponse toResult(StubPayment payment) {
        return switch (payment.paymentStatus()) {
            case PENDING -> new PaymentResultResponse(
                    PAYMENT_KEY, payment.orderId(), PaymentStatus.PENDING,
                    POLL_AFTER_SECONDS, null, null, null, null, null, null, null);
            case COMPLETED -> new PaymentResultResponse(
                    PAYMENT_KEY, payment.orderId(), PaymentStatus.COMPLETED,
                    null, payment.reservationId(), EXPECTED_AMOUNT, METHOD,
                    payment.reservationStatus(), APPROVED_AT, null, null);
            case FAILED -> new PaymentResultResponse(
                    PAYMENT_KEY, payment.orderId(), PaymentStatus.FAILED,
                    null, payment.reservationId(), null, null, null, null,
                    FAILED_HOLD_ID, FAIL_REASON);
        };
    }

    private static Map<String, StubPayment> createPayments() {
        Map<String, StubPayment> payments = new LinkedHashMap<>();
        payments.put(ACCEPTED_ORDER_ID,
                new StubPayment(ACCEPTED_ORDER_ID, 501L, PaymentStatus.PENDING, "PENDING_PAYMENT", true));
        payments.put(COMPLETED_ORDER_ID,
                new StubPayment(COMPLETED_ORDER_ID, 502L, PaymentStatus.COMPLETED, "CONFIRMED", true));
        payments.put(OTHER_MEMBER_ORDER_ID,
                new StubPayment(OTHER_MEMBER_ORDER_ID, 503L, PaymentStatus.COMPLETED, "CONFIRMED", false));
        payments.put(CANCELLED_ORDER_ID,
                new StubPayment(CANCELLED_ORDER_ID, 504L, PaymentStatus.PENDING, "CANCELLED", true));
        payments.put(EXPIRED_ORDER_ID,
                new StubPayment(EXPIRED_ORDER_ID, 505L, PaymentStatus.PENDING, "EXPIRED", true));
        payments.put(FAILED_ORDER_ID,
                new StubPayment(FAILED_ORDER_ID, 506L, PaymentStatus.FAILED, "PENDING_PAYMENT", true));
        return Map.copyOf(payments);
    }

    static String reservationStatusOf(String orderId) {
        return PAYMENTS.get(orderId).reservationStatus();
    }

    private record StubPayment(
            String orderId,
            long reservationId,
            PaymentStatus paymentStatus,
            String reservationStatus,
            boolean owned) {
    }
}
