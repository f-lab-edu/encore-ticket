package com.encore.ticket.booking.controller;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import com.encore.ticket.booking.api.dto.QueueStatus;
import com.encore.ticket.booking.api.dto.QueueStatusResponse;
import com.encore.ticket.booking.api.dto.QueueTokenResponse;

final class StubQueue {

    static final String WAITING_TOKEN = "q_waiting";

    static final String ADMITTED_TOKEN = "q_admitted";

    static final String UNKNOWN_TOKEN = "q_unknown";

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private static final OffsetDateTime ADMITTED_UNTIL =
            OffsetDateTime.of(2026, 8, 1, 20, 15, 0, 0, KST);

    private static final int WAITING_POSITION = 153;

    private static final int WAITING_ESTIMATED_WAIT_SECONDS = 300;

    private static final int WAITING_POLL_AFTER_SECONDS = 22;

    private static final int LAPSES_REMAINING = 2;

    private static final Set<String> TOKENS = Set.of(WAITING_TOKEN, ADMITTED_TOKEN);

    private StubQueue() {
    }

    static boolean exists(String queueToken) {
        return TOKENS.contains(queueToken);
    }

    static boolean admitted(String queueToken) {
        return ADMITTED_TOKEN.equals(queueToken);
    }

    static QueueTokenResponse enter(long scheduleId) {
        return new QueueTokenResponse(
                WAITING_TOKEN,
                scheduleId,
                QueueStatus.WAITING,
                WAITING_POSITION,
                WAITING_ESTIMATED_WAIT_SECONDS,
                WAITING_POLL_AFTER_SECONDS,
                false,
                LAPSES_REMAINING);
    }

    static QueueStatusResponse status(String queueToken) {
        if (admitted(queueToken)) {
            return new QueueStatusResponse(QueueStatus.ADMITTED, 0, null, null, ADMITTED_UNTIL);
        }

        return new QueueStatusResponse(
                QueueStatus.WAITING,
                WAITING_POSITION,
                WAITING_ESTIMATED_WAIT_SECONDS,
                WAITING_POLL_AFTER_SECONDS,
                null);
    }
}
