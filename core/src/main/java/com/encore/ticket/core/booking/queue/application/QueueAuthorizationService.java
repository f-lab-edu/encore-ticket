package com.encore.ticket.core.booking.queue.application;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

import com.encore.ticket.core.booking.exception.QueueNotAdmittedException;
import com.encore.ticket.core.booking.exception.QueueTokenExpiredException;
import com.encore.ticket.core.booking.exception.QueueTokenNotOwnedException;
import com.encore.ticket.core.booking.queue.domain.QueueAuthorizationPolicy;
import com.encore.ticket.core.booking.queue.port.QueueAuthorizationOutcome;
import com.encore.ticket.core.booking.queue.port.QueueRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueueAuthorizationService {

    private final QueueRepository queueRepository;
    private final QueueAuthorizationPolicy policy;
    private final Clock clock;

    public void authorize(Long scheduleId, Long memberId, String queueToken) {
        QueueAuthorizationOutcome outcome = queueRepository.authorizeAndRenew(
                scheduleId,
                queueToken,
                memberId,
                OffsetDateTime.now(clock),
                policy.renewalWindow());

        switch (outcome) {
            case AUTHORIZED -> {
                return;
            }
            case NOT_OWNED -> throw new QueueTokenNotOwnedException();
            case EXPIRED -> throw new QueueTokenExpiredException();
            case NOT_FOUND, NOT_ADMITTED -> throw new QueueNotAdmittedException();
        }
    }
}
