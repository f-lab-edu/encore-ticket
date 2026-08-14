package com.encore.ticket.storage.redis.booking.queue;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import com.encore.ticket.core.booking.dto.QueueStatus;
import com.encore.ticket.core.booking.queue.domain.QueueToken;
import com.encore.ticket.core.booking.queue.port.QueueEnterResult;
import com.encore.ticket.core.booking.queue.port.QueuePollOutcome;
import com.encore.ticket.core.booking.queue.port.QueuePollResult;
import com.encore.ticket.core.booking.queue.port.QueueRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class QueueRedisRepository implements QueueRepository {

    private static final String TOKEN_PREFIX = "q_";

    private static final long CLEANUP_SLACK_MILLIS = 1_000L;

    private static final String CREATED = "1";
    private static final int OUTCOME = 0;
    private static final int TOKEN = 1;
    private static final int MEMBER_ID = 2;
    private static final int POSITION = 3;
    private static final int STATUS = 4;
    private static final int LAST_POLLED_AT = 5;
    private static final int LAPSES_REMAINING = 6;
    private static final int ADMITTED_UNTIL = 8;
    private static final int SEQUENCE = 9;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> enterOrResumeScript;
    private final RedisScript<List> recordPollScript;
    private final Clock clock;

    @Override
    public QueueEnterResult enterOrResume(Long scheduleId, Long memberId, OffsetDateTime now) {
        List<String> reply = execute(
                enterOrResumeScript,
                List.of(
                        QueueRedisKeys.sequence(scheduleId),
                        QueueRedisKeys.waiting(scheduleId),
                        QueueRedisKeys.expiry(scheduleId),
                        QueueRedisKeys.member(scheduleId, memberId)),
                String.valueOf(millis(now)),
                TOKEN_PREFIX + UUID.randomUUID(),
                String.valueOf(memberId),
                QueueRedisKeys.schedule(scheduleId),
                String.valueOf(QueueToken.grace().toMillis()),
                String.valueOf(QueueToken.MAX_LAPSES),
                String.valueOf(QueueToken.hardExpiry().toMillis()),
                String.valueOf(CLEANUP_SLACK_MILLIS));

        return new QueueEnterResult(
                toToken(scheduleId, reply), CREATED.equals(reply.get(OUTCOME)));
    }

    @Override
    public QueuePollResult recordPoll(
            Long scheduleId, String queueToken, Long memberId, OffsetDateTime now) {
        List<String> reply = execute(
                recordPollScript,
                List.of(
                        QueueRedisKeys.sequence(scheduleId),
                        QueueRedisKeys.waiting(scheduleId),
                        QueueRedisKeys.expiry(scheduleId),
                        QueueRedisKeys.token(scheduleId, queueToken),
                        QueueRedisKeys.member(scheduleId, memberId)),
                String.valueOf(millis(now)),
                queueToken,
                String.valueOf(memberId),
                QueueRedisKeys.schedule(scheduleId),
                String.valueOf(QueueToken.grace().toMillis()),
                String.valueOf(QueueToken.hardExpiry().toMillis()),
                String.valueOf(CLEANUP_SLACK_MILLIS));

        QueuePollOutcome outcome = QueuePollOutcome.valueOf(reply.get(OUTCOME));
        if (outcome != QueuePollOutcome.UPDATED) {
            return QueuePollResult.of(outcome);
        }
        return QueuePollResult.updated(toToken(scheduleId, reply));
    }

    @Override
    public Optional<QueueToken> findByToken(Long scheduleId, String queueToken) {
        Map<Object, Object> fields = redisTemplate.opsForHash()
                .entries(QueueRedisKeys.token(scheduleId, queueToken));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        if (Long.parseLong(required(fields, "hardExpiresAt")) < Instant.now(clock).toEpochMilli()) {
            return Optional.empty();
        }

        Long rank = redisTemplate.opsForZSet()
                .rank(QueueRedisKeys.waiting(scheduleId), queueToken);
        Object admittedUntil = fields.get("admittedUntil");

        return Optional.of(QueueToken.builder()
                .token(queueToken)
                .scheduleId(scheduleId)
                .memberId(Long.valueOf(required(fields, "memberId")))
                .position(rank == null ? 0 : rank.intValue() + 1)
                .sequence(Integer.parseInt(required(fields, "sequence")))
                .status(QueueStatus.valueOf(required(fields, "status")))
                .lastPolledAt(toOffset(required(fields, "lastPolledAt")))
                .lapsesRemaining(Integer.parseInt(required(fields, "lapsesRemaining")))
                .admittedUntil(admittedUntil == null ? null : toOffset(admittedUntil.toString()))
                .build());
    }

    @SuppressWarnings("unchecked")
    private List<String> execute(RedisScript<List> script, List<String> keys, String... args) {
        List<String> reply = redisTemplate.execute(script, keys, (Object[]) args);
        if (reply == null || reply.isEmpty()) {
            throw new IllegalStateException("Redis 대기열 스크립트가 결과를 반환하지 않았습니다.");
        }
        return reply;
    }

    private QueueToken toToken(Long scheduleId, List<String> reply) {
        String admittedUntil = reply.get(ADMITTED_UNTIL);
        return QueueToken.builder()
                .token(reply.get(TOKEN))
                .scheduleId(scheduleId)
                .memberId(Long.valueOf(reply.get(MEMBER_ID)))
                .position(Integer.parseInt(reply.get(POSITION)))
                .sequence(Integer.parseInt(reply.get(SEQUENCE)))
                .status(QueueStatus.valueOf(reply.get(STATUS)))
                .lastPolledAt(toOffset(reply.get(LAST_POLLED_AT)))
                .lapsesRemaining(Integer.parseInt(reply.get(LAPSES_REMAINING)))
                .admittedUntil(admittedUntil.isEmpty() ? null : toOffset(admittedUntil))
                .build();
    }

    private long millis(OffsetDateTime time) {
        return time.toInstant().toEpochMilli();
    }

    private OffsetDateTime toOffset(String epochMillis) {
        return Instant.ofEpochMilli(Long.parseLong(epochMillis)).atOffset(ZoneOffset.UTC);
    }

    private String required(Map<Object, Object> fields, String name) {
        Object value = fields.get(name);
        if (value == null) {
            throw new IllegalStateException("Redis 대기열 토큰에 필수 필드가 없습니다: " + name);
        }
        return value.toString();
    }
}
