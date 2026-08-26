package com.encore.ticket.core.booking.queue.application;

import com.encore.ticket.core.booking.dto.QueueStatusResponse;
import com.encore.ticket.core.booking.dto.QueueTokenResponse;
import com.encore.ticket.core.booking.exception.QueueTokenExpiredException;
import com.encore.ticket.core.booking.exception.QueueTokenNotOwnedException;
import com.encore.ticket.core.booking.queue.domain.QueueToken;
import com.encore.ticket.core.booking.queue.port.QueueEnterResult;
import com.encore.ticket.core.booking.queue.port.QueuePollResult;
import com.encore.ticket.core.booking.queue.port.QueueRepository;
import com.encore.ticket.core.exception.NotFoundException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueueService {

    private static final int POLL_AFTER_SECONDS = 20;
    private static final int ESTIMATED_WAIT_SECONDS_PER_POSITION = 2;
    private static final int ADMITTED_POSITION = 0;
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final QueueRepository queueRepository;
    private final Clock clock;

    public QueueTokenResponse enter(Long scheduleId, Long memberId) {
        QueueEnterResult result = queueRepository.enterOrResume(
                scheduleId, memberId, OffsetDateTime.now(clock));
        return toResponse(result.token(), !result.created());
    }

    public QueueStatusResponse status(Long scheduleId, String queueToken, Long memberId) {
        QueuePollResult result = queueRepository.recordPoll(
                scheduleId, queueToken, memberId, OffsetDateTime.now(clock));

        QueueToken token = switch (result.outcome()) {
            case UPDATED -> result.token();
            case NOT_FOUND -> throw new NotFoundException("존재하지 않는 대기열 토큰입니다.");
            case NOT_OWNED -> throw new QueueTokenNotOwnedException();
            case EXPIRED -> throw new QueueTokenExpiredException();
        };

        if (token.isAdmitted()) {
            if (token.isAdmissionExpired(clock)) {
                throw new QueueTokenExpiredException();
            }
            return new QueueStatusResponse(
                    token.status(), ADMITTED_POSITION, null, null,
                    token.admittedUntil().withOffsetSameInstant(KST).truncatedTo(ChronoUnit.SECONDS));
        }

        return new QueueStatusResponse(
                token.status(),
                token.position(),
                token.position() * ESTIMATED_WAIT_SECONDS_PER_POSITION,
                POLL_AFTER_SECONDS,
                null);
    }

    private QueueTokenResponse toResponse(QueueToken token, boolean resumed) {
        if (token.isAdmitted()) {
            return new QueueTokenResponse(
                    token.token(), token.scheduleId(), token.status(), ADMITTED_POSITION,
                    0, 0, resumed, token.lapsesRemaining());
        }
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
