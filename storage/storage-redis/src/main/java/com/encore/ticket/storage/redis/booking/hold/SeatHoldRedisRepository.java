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
import com.encore.ticket.core.booking.hold.port.SeatHoldRepository;
import com.encore.ticket.core.booking.reservation.domain.HeldSeats;
import com.encore.ticket.core.booking.reservation.port.HoldReader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SeatHoldRedisRepository implements SeatHoldRepository, HoldReader {

    private static final long ACQUIRED = 1L;
    private static final long SEAT_ALREADY_HELD = -1L;
    private static final long PURCHASE_LIMIT_EXCEEDED = -2L;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> acquireSeatHoldScript;
    private final Clock clock;

    @Override
    public SeatHoldAcquireResult acquire(SeatHold seatHold, int maxSeatsPerSchedule) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        long ttlMillis = Duration.between(now, seatHold.expiresAt()).toMillis();
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("이미 만료된 선점은 저장할 수 없습니다.");
        }

        List<String> keys = new ArrayList<>();
        keys.add(SeatHoldRedisKeys.scheduleSeats(seatHold.scheduleId()));
        keys.add(SeatHoldRedisKeys.memberSeats(seatHold.scheduleId(), seatHold.memberId()));
        keys.add(SeatHoldRedisKeys.hold(seatHold.holdId()));
        seatHold.seatIds().stream()
                .map(seatId -> SeatHoldRedisKeys.seat(seatHold.scheduleId(), seatId))
                .forEach(keys::add);

        String seatIds = seatHold.seatIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        Long code = redisTemplate.execute(
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
                seatHold.expiresAt().toString());

        if (code == null) {
            throw new IllegalStateException("Redis 선점 스크립트가 결과를 반환하지 않았습니다.");
        }
        if (code == ACQUIRED) {
            return SeatHoldAcquireResult.ACQUIRED;
        }
        if (code == SEAT_ALREADY_HELD) {
            return SeatHoldAcquireResult.SEAT_ALREADY_HELD;
        }
        if (code == PURCHASE_LIMIT_EXCEEDED) {
            return SeatHoldAcquireResult.PURCHASE_LIMIT_EXCEEDED;
        }
        throw new IllegalStateException("알 수 없는 Redis 선점 결과입니다: " + code);
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
