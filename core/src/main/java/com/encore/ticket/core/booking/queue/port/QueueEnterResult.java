package com.encore.ticket.core.booking.queue.port;

import com.encore.ticket.core.booking.queue.domain.QueueToken;

public record QueueEnterResult(QueueToken token, boolean created) {
}
