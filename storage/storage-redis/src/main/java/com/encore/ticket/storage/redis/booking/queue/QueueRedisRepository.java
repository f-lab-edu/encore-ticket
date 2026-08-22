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
import org.springframework.stereotype.Repository;

import com.encore.ticket.core.booking.dto.QueueStatus;
import com.encore.ticket.core.booking.queue.domain.QueueAdmissionPolicy;
import com.encore.ticket.core.booking.queue.domain.QueuePolicy;
import com.encore.ticket.core.booking.queue.domain.QueueToken;
import com.encore.ticket.core.booking.queue.port.QueueAdmissionResult;
import com.encore.ticket.core.booking.queue.port.QueueEnterResult;
import com.encore.ticket.core.booking.queue.port.QueuePollOutcome;
import com.encore.ticket.core.booking.queue.port.QueuePollResult;
import com.encore.ticket.core.booking.queue.port.QueueRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class QueueRedisRepository implements QueueRepository {

    private static final String TOKEN_PREFIX = "q_";
    private static final String CREATED = "1";

    private static final long CLEANUP_SLACK_MILLIS = 1_000L;
    private static final int REQUEST_PURGE_LIMIT = 50;
    private static final int SCHEDULE_SCAN_LIMIT = 200;

    private final StringRedisTemplate redisTemplate;
    private final QueueFunctions functions;
    private final QueuePolicy policy;
    private final Clock clock;

    @Override
    public QueueEnterResult enterOrResume(Long scheduleId, Long memberId, OffsetDateTime now) {
        redisTemplate.opsForZSet()
                .add(QueueRedisKeys.schedules(), String.valueOf(scheduleId), millis(now));
        Map<String, String> reply = functions.call(
                QueueFunctions.ENTER_OR_RESUME,
                List.of(
                        QueueRedisKeys.sequence(scheduleId),
                        QueueRedisKeys.waiting(scheduleId),
                        QueueRedisKeys.expiry(scheduleId),
                        QueueRedisKeys.member(scheduleId, memberId),
                        QueueRedisKeys.admitted(scheduleId),
                        QueueRedisKeys.admitted(),
                        QueueRedisKeys.admissionWaiting(scheduleId),
                        QueueRedisKeys.admissionSchedules()),
                String.valueOf((long) millis(now)),
                TOKEN_PREFIX + UUID.randomUUID(),
                String.valueOf(memberId),
                QueueRedisKeys.schedule(scheduleId),
                String.valueOf(policy.grace().toMillis()),
                String.valueOf(policy.maxLapses()),
                String.valueOf(policy.hardExpiry().toMillis()),
                String.valueOf(CLEANUP_SLACK_MILLIS),
                String.valueOf(REQUEST_PURGE_LIMIT),
                String.valueOf(scheduleId));

        return new QueueEnterResult(
                toToken(scheduleId, reply), CREATED.equals(required(reply, "created")));
    }

    @Override
    public QueuePollResult recordPoll(
            Long scheduleId, String queueToken, Long memberId, OffsetDateTime now) {
        Map<String, String> reply = functions.call(
                QueueFunctions.RECORD_POLL,
                List.of(
                        QueueRedisKeys.sequence(scheduleId),
                        QueueRedisKeys.waiting(scheduleId),
                        QueueRedisKeys.expiry(scheduleId),
                        QueueRedisKeys.token(scheduleId, queueToken),
                        QueueRedisKeys.member(scheduleId, memberId),
                        QueueRedisKeys.admitted(scheduleId),
                        QueueRedisKeys.admitted(),
                        QueueRedisKeys.admissionWaiting(scheduleId),
                        QueueRedisKeys.admissionSchedules()),
                String.valueOf((long) millis(now)),
                queueToken,
                String.valueOf(memberId),
                QueueRedisKeys.schedule(scheduleId),
                String.valueOf(policy.grace().toMillis()),
                String.valueOf(policy.hardExpiry().toMillis()),
                String.valueOf(CLEANUP_SLACK_MILLIS),
                String.valueOf(REQUEST_PURGE_LIMIT),
                String.valueOf(scheduleId));

        QueuePollOutcome outcome = QueuePollOutcome.valueOf(required(reply, "outcome"));
        if (outcome != QueuePollOutcome.UPDATED) {
            return QueuePollResult.of(outcome);
        }
        return QueuePollResult.updated(toToken(scheduleId, reply));
    }

    @Override
    public QueueAdmissionResult admit(OffsetDateTime now, QueueAdmissionPolicy admissionPolicy) {
        String owner = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                QueueRedisKeys.admissionExecutionLease(), owner, admissionPolicy.executionLease());
        if (!Boolean.TRUE.equals(acquired)) {
            return QueueAdmissionResult.leaseNotAcquired();
        }

        try {
            Map<String, String> reply = functions.call(
                    QueueFunctions.ADMIT,
                    List.of(
                            QueueRedisKeys.admissionSchedules(),
                            QueueRedisKeys.admissionCursor(),
                            QueueRedisKeys.admitted()),
                    String.valueOf((long) millis(now)),
                    QueueRedisKeys.root(),
                    String.valueOf(admissionPolicy.waitingActivityWindow().toMillis()),
                    String.valueOf(admissionPolicy.initialLease().toMillis()),
                    String.valueOf(admissionPolicy.hardCap().toMillis()),
                    String.valueOf(admissionPolicy.perScheduleCapacity()),
                    String.valueOf(admissionPolicy.globalCapacity()),
                    String.valueOf(admissionPolicy.maxAdmissionsPerRun()),
                    String.valueOf(admissionPolicy.candidateScanLimit()),
                    String.valueOf(SCHEDULE_SCAN_LIMIT),
                    String.valueOf(CLEANUP_SLACK_MILLIS));
            return QueueAdmissionResult.completed(Integer.parseInt(required(reply, "admitted")));
        } finally {
            functions.call(
                    QueueFunctions.RELEASE_ADMISSION_LEASE,
                    List.of(QueueRedisKeys.admissionExecutionLease()),
                    owner);
        }
    }

    @Override
    public Optional<QueueToken> findByToken(Long scheduleId, String queueToken) {
        Map<Object, Object> fields = redisTemplate.opsForHash()
                .entries(QueueRedisKeys.token(scheduleId, queueToken));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        if (Long.parseLong(hashField(fields, "hardExpiresAt")) < Instant.now(clock).toEpochMilli()) {
            return Optional.empty();
        }
        String status = hashField(fields, "status");
        if ("EXPIRED".equals(status)) {
            return Optional.empty();
        }

        Long rank = redisTemplate.opsForZSet()
                .rank(QueueRedisKeys.waiting(scheduleId), queueToken);
        Object admittedUntil = fields.get("admittedUntil");

        return Optional.of(QueueToken.builder()
                .token(queueToken)
                .scheduleId(scheduleId)
                .memberId(Long.valueOf(hashField(fields, "memberId")))
                .position(rank == null ? 0 : rank.intValue() + 1)
                .sequence(Integer.parseInt(hashField(fields, "sequence")))
                .status(QueueStatus.valueOf(status))
                .lastPolledAt(toOffset(hashField(fields, "lastPolledAt")))
                .lapsesRemaining(Integer.parseInt(hashField(fields, "lapsesRemaining")))
                .admittedUntil(admittedUntil == null ? null : toOffset(admittedUntil.toString()))
                .build());
    }

    private QueueToken toToken(Long scheduleId, Map<String, String> reply) {
        QueueToken.QueueTokenBuilder builder = QueueToken.builder()
                .token(required(reply, "token"))
                .scheduleId(scheduleId)
                .memberId(Long.valueOf(required(reply, "memberId")))
                .position(Integer.parseInt(required(reply, "position")))
                .sequence(Integer.parseInt(required(reply, "sequence")))
                .status(QueueStatus.valueOf(required(reply, "status")))
                .lastPolledAt(toOffset(required(reply, "lastPolledAt")))
                .lapsesRemaining(Integer.parseInt(required(reply, "lapsesRemaining")));
        String admittedUntil = reply.get("admittedUntil");
        if (admittedUntil != null) {
            builder.admittedUntil(toOffset(admittedUntil));
        }
        return builder.build();
    }

    private double millis(OffsetDateTime time) {
        return time.toInstant().toEpochMilli();
    }

    private OffsetDateTime toOffset(String epochMillis) {
        return Instant.ofEpochMilli(Long.parseLong(epochMillis)).atOffset(ZoneOffset.UTC);
    }

    private String required(Map<String, String> reply, String name) {
        String value = reply.get(name);
        if (value == null) {
            throw new IllegalStateException("Redis 대기열 응답에 필수 필드가 없습니다: " + name);
        }
        return value;
    }

    private String hashField(Map<Object, Object> fields, String name) {
        Object value = fields.get(name);
        if (value == null) {
            throw new IllegalStateException("Redis 대기열 토큰에 필수 필드가 없습니다: " + name);
        }
        return value.toString();
    }
}
