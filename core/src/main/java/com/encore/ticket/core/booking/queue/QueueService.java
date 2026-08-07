package com.encore.ticket.core.booking.queue;

import com.encore.ticket.core.booking.dto.QueueStatusResponse;
import com.encore.ticket.core.booking.dto.QueueTokenResponse;
import com.encore.ticket.core.booking.exception.QueueTokenExpiredException;
import com.encore.ticket.core.booking.exception.QueueTokenNotOwnedException;

import java.time.Clock;
import java.util.Optional;

class QueueService {

    private static final int POLL_AFTER_SECONDS = 20;
    private static final int ESTIMATED_WAIT_SECONDS_PER_POSITION = 2;
    private static final int ADMITTED_POSITION = 0;

    private final QueueRepository queueRepository;
    private final Clock clock;

    QueueService(QueueRepository queueRepository, Clock clock) {
        this.queueRepository = queueRepository;
        this.clock = clock;
    }

    QueueTokenResponse enter(Long scheduleId, Long memberId) {
        Optional<QueueToken> found = queueRepository.findActiveToken(scheduleId, memberId);

        if (found.isEmpty()) {
            return issueNew(scheduleId, memberId);
        }

        QueueToken existing = found.get();

        if (existing.isWithinGrace(clock)) {
            existing.recordPoll(clock);
            queueRepository.save(existing);
            return toResponse(existing, true);
        }

        if (existing.hasLapse()) {
            existing.useLapse();
            existing.recordPoll(clock);
            queueRepository.save(existing);
            return toResponse(existing, true);
        }

        return issueNew(scheduleId, memberId);
    }

    QueueStatusResponse status(Long scheduleId, String queueToken, Long memberId) {
        QueueToken token = queueRepository.findByToken(scheduleId, queueToken);
        if (!token.isOwnedBy(memberId)) {
            throw new QueueTokenNotOwnedException();
        }

        if (token.isAdmitted()) {
            if (token.isAdmissionExpired(clock)) {
                throw new QueueTokenExpiredException();
            }
            return new QueueStatusResponse(
                    token.status(), ADMITTED_POSITION, null, null, token.admittedUntil());
        }

        if (!token.isWithinGrace(clock)) {
            if (!token.hasLapse()) {
                throw new QueueTokenExpiredException();
            }
            token.useLapse();
        }
        token.recordPoll(clock);
        queueRepository.save(token);

        return new QueueStatusResponse(
                token.status(),
                token.position(),
                token.position() * ESTIMATED_WAIT_SECONDS_PER_POSITION,
                POLL_AFTER_SECONDS,
                null);
    }

    private QueueTokenResponse issueNew(Long scheduleId, Long memberId) {
        int position = queueRepository.countWaiting(scheduleId) + 1;
        QueueToken token = QueueToken.issue(scheduleId, memberId, position, clock);
        queueRepository.save(token);
        return toResponse(token, false);
    }

    private QueueTokenResponse toResponse(QueueToken token, boolean resumed) {
        return new QueueTokenResponse(
                token.token(),
                token.scheduleId(),
                token.status(),
                token.position(),
                token.position() * ESTIMATED_WAIT_SECONDS_PER_POSITION,
                POLL_AFTER_SECONDS,
                resumed,
                token.lapsesRemaining());
    }
}
