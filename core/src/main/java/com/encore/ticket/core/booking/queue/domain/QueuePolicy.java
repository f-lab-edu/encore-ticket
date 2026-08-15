package com.encore.ticket.core.booking.queue.domain;

import java.time.Duration;

public record QueuePolicy(Duration grace, int maxLapses) {

    public static final QueuePolicy DEFAULT = new QueuePolicy(Duration.ofMinutes(5), 2);

    public QueuePolicy {
        if (grace.isZero() || grace.isNegative()) {
            throw new IllegalArgumentException("유예는 0보다 길어야 합니다.");
        }
        if (maxLapses < 0) {
            throw new IllegalArgumentException("유예 횟수는 음수일 수 없습니다.");
        }
    }

    public Duration hardExpiry() {
        return grace.multipliedBy(maxLapses + 1L);
    }
}
