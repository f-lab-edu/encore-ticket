package com.encore.ticket.core.booking.queue.port;

import com.encore.ticket.core.booking.queue.domain.QueueToken;

/**
 * {@code token} 은 {@link QueuePollOutcome#UPDATED} 일 때만 채운다.
 * 나머지 결과는 저장소가 이미 토큰을 정리했거나 갱신하지 않았다는 뜻이다.
 */
public record QueuePollResult(QueuePollOutcome outcome, QueueToken token) {

    public static QueuePollResult updated(QueueToken token) {
        return new QueuePollResult(QueuePollOutcome.UPDATED, token);
    }

    public static QueuePollResult of(QueuePollOutcome outcome) {
        return new QueuePollResult(outcome, null);
    }
}
