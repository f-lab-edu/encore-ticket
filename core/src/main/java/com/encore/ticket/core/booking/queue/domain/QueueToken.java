package com.encore.ticket.core.booking.queue.domain;

import com.encore.ticket.core.booking.dto.QueueStatus;

import java.time.Clock;
import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QueueToken {

    private final String token;
    private final Long scheduleId;
    private final Long memberId;
    private final int position;
    private final int sequence;

    private final QueueStatus status;
    private final OffsetDateTime lastPolledAt;
    private final int lapsesRemaining;
    private final OffsetDateTime admittedUntil;

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public boolean isAdmitted() {
        return status == QueueStatus.ADMITTED;
    }

    public boolean isAdmissionExpired(Clock clock) {
        return !OffsetDateTime.now(clock).isBefore(admittedUntil);
    }
}
