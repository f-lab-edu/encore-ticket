package com.encore.ticket.core.booking.queue.domain;

import java.time.Duration;

public record QueueAuthorizationPolicy(Duration renewalWindow) {

    public QueueAuthorizationPolicy {
        if (renewalWindow == null || renewalWindow.isZero() || renewalWindow.isNegative()) {
            throw new IllegalArgumentException("대기열 갱신 시간은 0보다 커야 합니다.");
        }
    }
}
