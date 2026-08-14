package com.encore.ticket.storage.redis.booking.queue;

/**
 * 한 회차의 key 는 모두 같은 {scheduleId} hash tag 를 쓴다.
 * Lua 가 실행 중에 만드는 token·member key 도 같은 slot 에 들어간다.
 */
final class QueueRedisKeys {

    private static final String PREFIX = "queue:";

    private QueueRedisKeys() {
    }

    static String schedule(Long scheduleId) {
        return PREFIX + "{%d}".formatted(scheduleId);
    }

    static String sequence(Long scheduleId) {
        return schedule(scheduleId) + ":sequence";
    }

    static String waiting(Long scheduleId) {
        return schedule(scheduleId) + ":waiting";
    }

    static String expiry(Long scheduleId) {
        return schedule(scheduleId) + ":expiry";
    }

    static String member(Long scheduleId, Long memberId) {
        return schedule(scheduleId) + ":member:" + memberId;
    }

    static String token(Long scheduleId, String queueToken) {
        return schedule(scheduleId) + ":token:" + queueToken;
    }
}
