package com.encore.ticket.core.payment.domain;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 최초 요청 이후 최대 5회. 각 대기 시간은 직전 시도가 끝난 시각부터 계산한다. */
public final class RefundRetryPolicy {
    private static final List<Duration> DELAYS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(4),
            Duration.ofMinutes(8), Duration.ofMinutes(16));

    private RefundRetryPolicy() {
    }

    public static Optional<OffsetDateTime> nextRetryAt(int completedRetries, OffsetDateTime finishedAt) {
        if (completedRetries < 0) {
            throw new IllegalArgumentException("재시도 횟수는 음수일 수 없습니다");
        }
        Objects.requireNonNull(finishedAt, "직전 시도 종료 시각이 필요합니다");
        if (completedRetries >= DELAYS.size()) {
            return Optional.empty();
        }
        return Optional.of(finishedAt.plus(DELAYS.get(completedRetries)));
    }
}
