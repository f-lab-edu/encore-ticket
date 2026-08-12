package com.encore.ticket.core.booking.queue.port;

import java.util.Optional;
import com.encore.ticket.core.booking.queue.domain.QueueToken;

public interface QueueRepository {
    public Optional<QueueToken> findActiveToken(Long scheduleId, Long memberId);

    public QueueToken findByToken(Long scheduleId, String queueToken);

    public int countWaiting(Long scheduleId);

    public void save(QueueToken token);
}
