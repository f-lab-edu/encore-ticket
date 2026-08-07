package com.encore.ticket.core.booking.queue;

import java.util.Optional;

interface QueueRepository {
    Optional<QueueToken> findActiveToken(Long scheduleId, Long memberId);

    QueueToken findByToken(Long scheduleId, String queueToken);

    int countWaiting(Long scheduleId);

    void save(QueueToken token);
}
