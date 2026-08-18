package com.encore.ticket.core.booking.queue.port;

import com.encore.ticket.core.booking.queue.domain.QueueToken;

public record QueuePollResult(QueuePollOutcome outcome, QueueToken token) {

    public static QueuePollResult updated(QueueToken token) {
        return new QueuePollResult(QueuePollOutcome.UPDATED, token);
    }

    public static QueuePollResult of(QueuePollOutcome outcome) {
        return new QueuePollResult(outcome, null);
    }
}
