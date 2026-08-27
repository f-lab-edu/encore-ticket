package com.encore.ticket.storage.redis.booking.hold;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import com.encore.ticket.core.booking.hold.domain.SeatHold;
import com.encore.ticket.core.booking.hold.port.SeatHoldAcquireResult;
import com.encore.ticket.core.booking.hold.port.SeatHoldAcquisition;
import com.encore.ticket.core.booking.hold.port.SeatHoldRepository;
import com.encore.ticket.core.booking.reservation.domain.HeldSeats;
import com.encore.ticket.core.booking.reservation.port.HoldReader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SeatHoldRedisRepository implements SeatHoldRepository, HoldReader {

    private static final String ACQUIRED = "1";
    private static final String REPLAYED = "2";
    private static final String SEAT_ALREADY_HELD = "-1";
    private static final String PURCHASE_LIMIT_EXCEEDED = "-2";
    private static final String IDEMPOTENCY_KEY_REUSED = "-3";

    private final StringRedisTemplate redisTemplate;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> acquireSeatHoldScript;
    private final Clock clock;

    @Override
    @SuppressWarnings("unchecked")
    public SeatHoldAcquisition acquire(
            SeatHold seatHold,
            int maxSeatsPerSchedule,
            String idempotencyKey,
            String requestFingerprint) {

        OffsetDateTime now = OffsetDateTime.now(clock);
        long ttlMillis = Duration.between(now, seatHold.expiresAt()).toMillis();
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("이미 만료된 선점은 저장할 수 없습니다.");
        }

        List<String> keys = new ArrayList<>();
        keys.add(SeatHoldRedisKeys.scheduleSeats(seatHold.scheduleId()));
        keys.add(SeatHoldRedisKeys.memberSeats(seatHold.scheduleId(), seatHold.memberId()));
        keys.add(SeatHoldRedisKeys.hold(seatHold.holdId()));
        keys.add(SeatHoldRedisKeys.idempotency(
                seatHold.scheduleId(), seatHold.memberId(), idempotencyKey));
        seatHold.seatIds().stream()
                .map(seatId -> SeatHoldRedisKeys.seat(seatHold.scheduleId(), seatId))
                .forEach(keys::add);

        String seatIds = seatHold.seatIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        List<String> result = redisTemplate.execute(
                acquireSeatHoldScript,
                keys,
                String.valueOf(now.toInstant().toEpochMilli()),
                String.valueOf(seatHold.expiresAt().toInstant().toEpochMilli()),
                String.valueOf(ttlMillis),
                String.valueOf(maxSeatsPerSchedule),
                seatHold.holdId(),
                String.valueOf(seatHold.scheduleId()),
                String.valueOf(seatHold.memberId()),
                seatIds,
                seatHold.expiresAt().toString(),
                requestFingerprint);

        if (result == null || result.size() != 3) {
            throw new IllegalStateException("Redis 선점 스크립트가 결과를 반환하지 않았습니다.");
        }

        return switch (result.get(0)) {
            case ACQUIRED -> new SeatHoldAcquisition(
                    SeatHoldAcquireResult.ACQUIRED, seatHold.holdId(), seatHold.expiresAt());
            case REPLAYED -> new SeatHoldAcquisition(
                    SeatHoldAcquireResult.REPLAYED,
                    result.get(1),
                    OffsetDateTime.parse(result.get(2)));
            case SEAT_ALREADY_HELD ->
                    SeatHoldAcquisition.failed(SeatHoldAcquireResult.SEAT_ALREADY_HELD);
            case PURCHASE_LIMIT_EXCEEDED ->
                    SeatHoldAcquisition.failed(SeatHoldAcquireResult.PURCHASE_LIMIT_EXCEEDED);
            case IDEMPOTENCY_KEY_REUSED ->
                    SeatHoldAcquisition.failed(SeatHoldAcquireResult.IDEMPOTENCY_KEY_REUSED);
            default -> throw new IllegalStateException(
                    "알 수 없는 Redis 선점 결과입니다: " + result.get(0));
        };
    }

    @Override
    public Map<Long, OffsetDateTime> holdExpiryBySeatId(Long scheduleId) {
        String key = SeatHoldRedisKeys.scheduleSeats(scheduleId);
        long nowMillis = Instant.now(clock).toEpochMilli();
        redisTemplate.opsForZSet().removeRangeByScore(
                key, Double.NEGATIVE_INFINITY, nowMillis);

        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .rangeByScoreWithScores(key, nowMillis, Double.POSITIVE_INFINITY);
        if (tuples == null || tuples.isEmpty()) {
            return Map.of();
        }

        Map<Long, OffsetDateTime> result = new LinkedHashMap<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() == null || tuple.getScore() == null) {
                continue;
            }
            result.put(
                    Long.valueOf(tuple.getValue()),
                    Instant.ofEpochMilli(tuple.getScore().longValue())
                            .atOffset(ZoneOffset.UTC));
        }
        return result;
    }

    @Override
    public Optional<HeldSeats> findByHoldId(String holdId) {
        Map<Object, Object> fields = redisTemplate.opsForHash()
                .entries(SeatHoldRedisKeys.hold(holdId));
        if (fields.isEmpty()) {
            return Optional.empty();
        }

        List<Long> seatIds = Arrays.stream(required(fields, "seatIds").split(","))
                .map(Long::valueOf)
                .toList();
        return Optional.of(new HeldSeats(
                holdId,
                Long.valueOf(required(fields, "scheduleId")),
                seatIds,
                Long.valueOf(required(fields, "memberId")),
                OffsetDateTime.parse(required(fields, "expiresAt"))));
    }

    private String required(Map<Object, Object> fields, String name) {
        Object value = fields.get(name);
        if (value == null) {
            throw new IllegalStateException(
                    "Redis 선점 데이터에 필수 필드가 없습니다: " + name);
        }
        return value.toString();
    }
}
