package com.encore.ticket.storage.redis.booking.queue;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.encore.ticket.core.booking.queue.domain.QueuePolicy;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class QueueExpirySweeper {

    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES_PER_SCHEDULE = 20;
    private static final int MAX_SCHEDULES_PER_SWEEP = 200;
    private static final int MAX_RETIRE_PER_SWEEP = 100;

    private final StringRedisTemplate redisTemplate;
    private final QueueFunctions functions;
    private final QueuePolicy policy;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${ticket.queue.sweep-interval-ms:1000}")
    public void sweep() {
        sweepAt(OffsetDateTime.now(clock));
    }

    int sweepAt(OffsetDateTime now) {
        long nowMillis = now.toInstant().toEpochMilli();
        long horizon = nowMillis - retentionMillis();
        String registry = QueueRedisKeys.schedules();

        int purged = 0;
        for (String scheduleId : range(registry, horizon, Double.POSITIVE_INFINITY,
                MAX_SCHEDULES_PER_SWEEP)) {
            purged += sweepSchedule(Long.valueOf(scheduleId), nowMillis).purged();
        }

        for (String scheduleId : range(registry, Double.NEGATIVE_INFINITY, horizon,
                MAX_RETIRE_PER_SWEEP)) {
            SweepOutcome outcome = sweepSchedule(Long.valueOf(scheduleId), nowMillis);
            purged += outcome.purged();
            if (!outcome.alive()) {
                redisTemplate.opsForZSet().remove(registry, scheduleId);
            }
        }
        return purged;
    }

    private Set<String> range(String registry, double min, double max, int limit) {
        Set<String> found = redisTemplate.opsForZSet().rangeByScore(registry, min, max, 0, limit);
        return found == null ? Set.of() : found;
    }

    private SweepOutcome sweepSchedule(Long scheduleId, long nowMillis) {
        int purged = 0;
        boolean alive = false;

        for (int round = 0; round < MAX_BATCHES_PER_SCHEDULE; round++) {
            Map<String, String> reply = functions.call(
                    QueueFunctions.SWEEP_EXPIRED,
                    List.of(
                            QueueRedisKeys.waiting(scheduleId),
                            QueueRedisKeys.expiry(scheduleId),
                            QueueRedisKeys.admitted(scheduleId),
                            QueueRedisKeys.admitted(),
                            QueueRedisKeys.admissionWaiting(scheduleId)),
                    String.valueOf(nowMillis),
                    QueueRedisKeys.schedule(scheduleId),
                    String.valueOf(BATCH_SIZE));

            purged += Integer.parseInt(reply.get("purged"));
            alive = Integer.parseInt(reply.get("alive")) > 0;

            if (Integer.parseInt(reply.get("remaining")) == 0) {
                break;
            }
        }

        if (alive) {
            redisTemplate.opsForZSet().add(
                    QueueRedisKeys.schedules(), String.valueOf(scheduleId), nowMillis);
        }
        return new SweepOutcome(purged, alive);
    }

    private long retentionMillis() {
        return policy.hardExpiry().plus(Duration.ofMinutes(1)).toMillis();
    }

    private record SweepOutcome(int purged, boolean alive) {
    }
}
