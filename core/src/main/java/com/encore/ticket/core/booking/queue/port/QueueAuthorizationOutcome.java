package com.encore.ticket.core.booking.queue.port;

public enum QueueAuthorizationOutcome {

    AUTHORIZED,
    NOT_FOUND,
    NOT_OWNED,
    NOT_ADMITTED,
    EXPIRED
}
