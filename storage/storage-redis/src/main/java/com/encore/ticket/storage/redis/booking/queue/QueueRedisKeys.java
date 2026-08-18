package com.encore.ticket.storage.redis.booking.queue;

final class QueueRedisKeys {

    private static final String PREFIX = "queue:";

    private QueueRedisKeys() {
    }

    static String schedules() {
        return PREFIX + "schedules";
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
