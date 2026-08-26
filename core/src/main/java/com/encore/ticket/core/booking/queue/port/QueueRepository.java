package com.encore.ticket.core.booking.queue.port;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.encore.ticket.core.booking.queue.domain.QueueAdmissionPolicy;
import com.encore.ticket.core.booking.queue.domain.QueueToken;

public interface QueueRepository {

    QueueEnterResult enterOrResume(Long scheduleId, Long memberId, OffsetDateTime now);

    Optional<QueueToken> findByToken(Long scheduleId, String queueToken);

    QueuePollResult recordPoll(Long scheduleId, String queueToken, Long memberId, OffsetDateTime now);

    QueueAdmissionResult admit(OffsetDateTime now, QueueAdmissionPolicy policy);
}
