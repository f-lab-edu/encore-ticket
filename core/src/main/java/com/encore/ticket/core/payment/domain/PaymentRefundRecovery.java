package com.encore.ticket.core.payment.domain;

import com.encore.ticket.core.payment.dto.RefundRecoveryCategory;
import java.time.OffsetDateTime;

/** 저장된 복구 판단 정보. nextRetryAt이 없다는 사실은 실행 허용을 뜻하지 않는다. */
public record PaymentRefundRecovery(
        RefundRecoveryCategory category, String errorCode, int retryCount,
        OffsetDateTime nextRetryAt, String stopReason) {

    public PaymentRefundRecovery {
        if (retryCount < 0) {
            throw new IllegalArgumentException("환불 재시도 횟수는 음수일 수 없습니다");
        }
    }

    public static PaymentRefundRecovery initial() {
        return new PaymentRefundRecovery(null, null, 0, null, null);
    }
}
