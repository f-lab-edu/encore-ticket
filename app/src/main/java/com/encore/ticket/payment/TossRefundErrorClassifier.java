package com.encore.ticket.payment;

import com.encore.ticket.core.payment.dto.RefundRecoveryCategory;

/**
 * Toss 결제 취소 오류의 복구 방향을 해석한다. 분류 자체는 재전송을 허용하지 않는다.
 * 공식 기준: https://docs.tosspayments.com/reference/error-codes (결제 취소)
 */
final class TossRefundErrorClassifier {

    private TossRefundErrorClassifier() {
    }

    static RefundRecoveryCategory classify(int httpStatus, String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return RefundRecoveryCategory.RESULT_CONFIRMATION_REQUIRED;
        }
        return switch (errorCode) {
            case "PROVIDER_ERROR" -> categoryForStatus(httpStatus, 400,
                    RefundRecoveryCategory.AUTOMATIC_RECOVERY_CANDIDATE);
            case "FORBIDDEN_CONSECUTIVE_REQUEST" -> categoryForStatus(httpStatus, 403,
                    RefundRecoveryCategory.AUTOMATIC_RECOVERY_CANDIDATE);
            case "FAILED_INTERNAL_SYSTEM_PROCESSING", "FAILED_REFUND_PROCESS",
                    "FAILED_METHOD_HANDLING_CANCEL", "COMMON_ERROR" ->
                    categoryForStatus(httpStatus, 500,
                            RefundRecoveryCategory.AUTOMATIC_RECOVERY_CANDIDATE);
            case "UNAUTHORIZED_KEY" -> categoryForStatus(httpStatus, 401,
                    RefundRecoveryCategory.CORRECTION_OR_REVIEW_REQUIRED);
            case "INVALID_REQUEST", "INVALID_REFUND_ACCOUNT_INFO",
                    "INVALID_REFUND_ACCOUNT_NUMBER", "INVALID_BANK",
                    "EXCEED_CANCEL_AMOUNT_DISCOUNT_AMOUNT", "REFUND_REJECTED",
                    "FORBIDDEN_BANK_REFUND_REQUEST", "INVALID_IDEMPOTENCY_KEY" ->
                    categoryForStatus(httpStatus, 400,
                            RefundRecoveryCategory.CORRECTION_OR_REVIEW_REQUIRED);
            case "INCORRECT_BASIC_AUTH_FORMAT", "FORBIDDEN_REQUEST",
                    "NOT_CANCELABLE_AMOUNT", "NOT_CANCELABLE_PAYMENT",
                    "EXCEED_MAX_REFUND_DUE", "NOT_ALLOWED_PARTIAL_REFUND_WAITING_DEPOSIT",
                    "NOT_ALLOWED_PARTIAL_REFUND", "NOT_AVAILABLE_BANK",
                    "NOT_CANCELABLE_PAYMENT_FOR_DORMANT_USER", "EXCEED_CANCEL_LIMIT" ->
                    categoryForStatus(httpStatus, 403,
                            RefundRecoveryCategory.CORRECTION_OR_REVIEW_REQUIRED);
            // 이미 환불됨, 처리 중, 금액 불일치 및 알려지지 않은 오류는 결과부터 확인한다.
            // 5xx라도 FAILED_PARTIAL_REFUND처럼 일시적/영구적 원인이 섞일 수 있다.
            default -> RefundRecoveryCategory.RESULT_CONFIRMATION_REQUIRED;
        };
    }

    private static RefundRecoveryCategory categoryForStatus(
            int actual, int expected, RefundRecoveryCategory category) {
        return actual == expected ? category : RefundRecoveryCategory.RESULT_CONFIRMATION_REQUIRED;
    }
}
