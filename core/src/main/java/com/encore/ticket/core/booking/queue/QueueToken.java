package com.encore.ticket.core.booking.queue;

import com.encore.ticket.core.booking.dto.QueueStatus;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

class QueueToken {

    private static final int MAX_LAPSES = 2;
    private static final int GRACE_MINUTES = 5;

    private final String token;
    private final Long scheduleId;
    private final Long memberId;
    private final int position;

    private QueueStatus status;
    private OffsetDateTime lastPolledAt;
    private int lapsesRemaining;
    private OffsetDateTime admittedUntil;

    QueueToken(String token, Long scheduleId, Long memberId, int position, QueueStatus status, OffsetDateTime lastPolledAt, int lapsesRemaining, OffsetDateTime admittedUntil) {
        this.token = token;
        this.scheduleId = scheduleId;
        this.memberId = memberId;
        this.position = position;
        this.status = status;
        this.lastPolledAt = lastPolledAt;
        this.lapsesRemaining = lapsesRemaining;
        this.admittedUntil = admittedUntil;
    }

    static QueueToken issue(Long scheduleId, Long memberId, int position, Clock clock) {
        return new QueueToken("q_" + UUID.randomUUID(), scheduleId, memberId, position, QueueStatus.WAITING, OffsetDateTime.now(clock), MAX_LAPSES, null);
    }

    boolean isWithinGrace(Clock clock) {
        return !OffsetDateTime.now(clock).isAfter(lastPolledAt.plusMinutes(GRACE_MINUTES));
    }

    void recordPoll(Clock clock) {
        lastPolledAt = OffsetDateTime.now(clock);
    }

    boolean hasLapse() {
        return lapsesRemaining > 0;
    }

    boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    boolean isAdmitted() {
        return status == QueueStatus.ADMITTED;
    }

    boolean isAdmissionExpired(Clock clock) {
        return !OffsetDateTime.now(clock).isBefore(admittedUntil);
    }

    void useLapse() {
        lapsesRemaining--;
    }

    Long scheduleId() {
        return scheduleId;
    }

    int position() {
        return position;
    }

    String token() {
        return token;
    }

    QueueStatus status() {
        return status;
    }

    int lapsesRemaining() {
        return lapsesRemaining;
    }

    OffsetDateTime admittedUntil() {
        return admittedUntil;
    }
}
