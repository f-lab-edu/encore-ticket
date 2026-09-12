package com.encore.ticket.payment;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.encore.ticket.core.payment.dto.RefundRecoveryCategory.*;
import static org.assertj.core.api.Assertions.assertThat;

class TossRefundErrorClassifierTest {

    @ParameterizedTest
    @CsvSource({
            "400, PROVIDER_ERROR",
            "403, FORBIDDEN_CONSECUTIVE_REQUEST",
            "500, FAILED_INTERNAL_SYSTEM_PROCESSING",
            "500, FAILED_REFUND_PROCESS",
            "500, FAILED_METHOD_HANDLING_CANCEL",
            "500, COMMON_ERROR"
    })
    void 문서상_일시적_오류만_자동_복구_후보로_분류한다(int status, String code) {
        assertThat(TossRefundErrorClassifier.classify(status, code))
                .isEqualTo(AUTOMATIC_RECOVERY_CANDIDATE);
    }

    @ParameterizedTest
    @CsvSource({
            "401, UNAUTHORIZED_KEY",
            "403, INCORRECT_BASIC_AUTH_FORMAT",
            "400, INVALID_REQUEST",
            "400, INVALID_REFUND_ACCOUNT_NUMBER",
            "400, INVALID_IDEMPOTENCY_KEY",
            "400, REFUND_REJECTED",
            "403, EXCEED_MAX_REFUND_DUE",
            "403, NOT_CANCELABLE_PAYMENT",
            "403, NOT_AVAILABLE_BANK"
    })
    void 설정_요청_정책_문제는_수정이나_검토를_요구한다(int status, String code) {
        assertThat(TossRefundErrorClassifier.classify(status, code))
                .isEqualTo(CORRECTION_OR_REVIEW_REQUIRED);
    }

    @ParameterizedTest
    @CsvSource({
            "400, ALREADY_CANCELED_PAYMENT",
            "400, ALREADY_REFUND_PAYMENT",
            "409, IDEMPOTENT_REQUEST_PROCESSING",
            "400, NOT_MATCHES_REFUNDABLE_AMOUNT",
            "404, NOT_FOUND_PAYMENT",
            "500, FAILED_PARTIAL_REFUND",
            "500, FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING",
            "500, NEW_PROVIDER_ERROR",
            "429, HTTP_429",
            "400, PROVIDER_ERROR_EXTRA",
            "500, INVALID_REQUEST",
            "401, PROVIDER_ERROR",
            "200, COMMON_ERROR"
    })
    void 결과_불명과_문서에_없는_조합을_자동_재시도나_확정_실패로_단정하지_않는다(
            int status, String code) {
        assertThat(TossRefundErrorClassifier.classify(status, code))
                .isEqualTo(RESULT_CONFIRMATION_REQUIRED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 오류_코드가_없으면_결과_확인이_필요하다(String code) {
        assertThat(TossRefundErrorClassifier.classify(500, code))
                .isEqualTo(RESULT_CONFIRMATION_REQUIRED);
    }
}
