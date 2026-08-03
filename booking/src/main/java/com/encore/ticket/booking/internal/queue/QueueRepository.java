package com.encore.ticket.booking.internal.queue;

import java.util.Optional;

interface QueueRepository {
    Optional<QueueToken> findActiveToken(Long scheduleId, Long memberId);

    QueueToken findByToken(Long scheduleId, String queueToken, Long memberId);

    int countWaiting(Long scheduleId);

    void save(QueueToken token);
}
