package com.encore.ticket.core.booking.queue.port;

public enum QueuePollOutcome {
    UPDATED,
    NOT_FOUND,
    NOT_OWNED,
    EXPIRED
}
