package com.encore.ticket.core.booking.queue.port;

import java.util.Optional;
import com.encore.ticket.core.booking.queue.domain.QueueToken;

import com.encore.ticket.core.exception.NotFoundException;

public interface QueueRepository {
    public Optional<QueueToken> findActiveToken(Long scheduleId, Long memberId);

    Optional<QueueToken> findByToken(Long scheduleId, String queueToken);

    default QueueToken getByToken(Long scheduleId, String queueToken) {
        return findByToken(scheduleId, queueToken)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 대기열 토큰입니다."));
    }

    public int countWaiting(Long scheduleId);

    public void save(QueueToken token);
}
