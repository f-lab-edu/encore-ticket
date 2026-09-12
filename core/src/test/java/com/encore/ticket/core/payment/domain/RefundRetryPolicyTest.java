package com.encore.ticket.core.payment.domain;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RefundRetryPolicyTest {
    private static final OffsetDateTime FINISHED_AT = OffsetDateTime.parse("2026-09-08T10:00:17Z");

    @ParameterizedTest
    @CsvSource({"0,1", "1,2", "2,4", "3,8", "4,16"})
    void 직전_시도_종료_시각을_기준으로_다음_재시도를_예약한다(int retries, int minutes) {
        assertThat(RefundRetryPolicy.nextRetryAt(retries, FINISHED_AT))
                .contains(FINISHED_AT.plusMinutes(minutes));
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6, Integer.MAX_VALUE})
    void 한도를_소진하면_추가_재시도_시각을_만들지_않는다(int retries) {
        assertThat(RefundRetryPolicy.nextRetryAt(retries, FINISHED_AT)).isEmpty();
    }

    @Test
    void 음수_횟수는_거절한다() {
        assertThatThrownBy(() -> RefundRetryPolicy.nextRetryAt(-1, FINISHED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
