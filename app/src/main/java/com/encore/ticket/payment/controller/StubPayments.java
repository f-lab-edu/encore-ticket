package com.encore.ticket.payment.controller;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.encore.ticket.payment.api.dto.PaymentConfirmResponse;
import com.encore.ticket.payment.api.dto.PaymentResultResponse;
import com.encore.ticket.payment.api.dto.PaymentStatus;

final class StubPayments {

    static final String ACCEPTED_ORDER_ID = "reservation-501-1";

    static final String COMPLETED_ORDER_ID = "reservation-502-1";

    static final String OTHER_MEMBER_ORDER_ID = "reservation-503-1";

    static final String CANCELLED_ORDER_ID = "reservation-504-1";

    static final String EXPIRED_ORDER_ID = "reservation-505-1";

    static final String FAILED_ORDER_ID = "reservation-506-1";

    static final String MISSING_ORDER_ID = "reservation-999-1";

    static final String UNKNOWN_PAYMENT_KEY = "tgen_unknown_key";

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

    static String paymentKeyOf(String orderId) {
        return Optional.ofNullable(PAYMENTS.get(orderId))
                .map(StubPayment::paymentKey)
                .orElse(null);
    }

    static boolean paymentKeyBoundToOtherOrder(String paymentKey, String orderId) {
        return PAYMENTS.values().stream()
                .anyMatch(payment -> payment.paymentKey().equals(paymentKey)
                        && !payment.orderId().equals(orderId));
    }

    static boolean exists(String orderId) {
        return PAYMENTS.containsKey(orderId);
    }

    static boolean ownedByStubMember(String orderId) {
        return Optional.ofNullable(PAYMENTS.get(orderId))
                .map(StubPayment::owned)
                .orElse(false);
    }

    static boolean settled(String orderId) {
        return Optional.ofNullable(PAYMENTS.get(orderId))
                .map(payment -> payment.paymentStatus() != PaymentStatus.PENDING)
                .orElse(false);
    }

    static String reservationStatusOf(String orderId) {
        return Optional.ofNullable(PAYMENTS.get(orderId))
                .map(StubPayment::reservationStatus)
                .orElse(null);
    }

    static PaymentConfirmResponse confirm(String orderId) {
        StubPayment payment = PAYMENTS.get(orderId);

        return switch (payment.paymentStatus()) {
            case PENDING -> new PaymentConfirmResponse(
                    payment.paymentKey(), orderId, PaymentStatus.PENDING,
                    null, null, null, null, null);
            case FAILED -> new PaymentConfirmResponse(
                    payment.paymentKey(), orderId, PaymentStatus.FAILED,
                    payment.reservationId(), null, null, null, null);
            case COMPLETED -> new PaymentConfirmResponse(
                    payment.paymentKey(), orderId, PaymentStatus.COMPLETED,
                    payment.reservationId(), EXPECTED_AMOUNT, METHOD,
                    payment.reservationStatus(), APPROVED_AT);
        };
    }

    static Optional<PaymentResultResponse> result(String orderId) {
        return Optional.ofNullable(PAYMENTS.get(orderId)).map(StubPayments::toResult);
    }

    private static PaymentResultResponse toResult(StubPayment payment) {
        return switch (payment.paymentStatus()) {
            case PENDING -> new PaymentResultResponse(
                    payment.paymentKey(), payment.orderId(), PaymentStatus.PENDING,
                    POLL_AFTER_SECONDS, null, null, null, null, null, null, null);
            case COMPLETED -> new PaymentResultResponse(
                    payment.paymentKey(), payment.orderId(), PaymentStatus.COMPLETED,
                    null, payment.reservationId(), EXPECTED_AMOUNT, METHOD,
                    payment.reservationStatus(), APPROVED_AT, null, null);
            case FAILED -> new PaymentResultResponse(
                    payment.paymentKey(), payment.orderId(), PaymentStatus.FAILED,
                    null, payment.reservationId(), null, null, null, null,
                    FAILED_HOLD_ID, FAIL_REASON);
        };
    }

    private static Map<String, StubPayment> createPayments() {
        Map<String, StubPayment> payments = new LinkedHashMap<>();
        payments.put(ACCEPTED_ORDER_ID,
                createPayment(ACCEPTED_ORDER_ID, 501L, PaymentStatus.PENDING, "PENDING_PAYMENT", true));
        payments.put(COMPLETED_ORDER_ID,
                createPayment(COMPLETED_ORDER_ID, 502L, PaymentStatus.COMPLETED, "CONFIRMED", true));
        payments.put(OTHER_MEMBER_ORDER_ID,
                createPayment(OTHER_MEMBER_ORDER_ID, 503L, PaymentStatus.COMPLETED, "CONFIRMED", false));
        payments.put(CANCELLED_ORDER_ID,
                createPayment(CANCELLED_ORDER_ID, 504L, PaymentStatus.PENDING, "CANCELLED", true));
        payments.put(EXPIRED_ORDER_ID,
                createPayment(EXPIRED_ORDER_ID, 505L, PaymentStatus.PENDING, "EXPIRED", true));
        payments.put(FAILED_ORDER_ID,
                createPayment(FAILED_ORDER_ID, 506L, PaymentStatus.FAILED, "PENDING_PAYMENT", true));
        return Collections.unmodifiableMap(payments);
    }

    private static StubPayment createPayment(
            String orderId, long reservationId, PaymentStatus paymentStatus,
            String reservationStatus, boolean owned) {

        return new StubPayment(
                orderId,
                "tgen_" + orderId.replace('-', '_'),
                reservationId,
                paymentStatus,
                reservationStatus,
                owned);
    }

    private record StubPayment(
            String orderId,
            String paymentKey,
            long reservationId,
            PaymentStatus paymentStatus,
            String reservationStatus,
            boolean owned) {
    }
}
