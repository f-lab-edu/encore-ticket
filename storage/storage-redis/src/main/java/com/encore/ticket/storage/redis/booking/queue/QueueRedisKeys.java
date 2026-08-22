package com.encore.ticket.storage.redis.booking.queue;

final class QueueRedisKeys {

    private static final String ROOT = "queue";
    private static final String PREFIX = ROOT + ":";

    private QueueRedisKeys() {
    }

    static String schedules() {
        return PREFIX + "schedules";
    }

    static String root() {
        return ROOT;
    }

    static String admissionSchedules() {
        return PREFIX + "admission:schedules";
    }

    static String admissionCursor() {
        return PREFIX + "admission:cursor";
    }

    static String admissionExecutionLease() {
        return PREFIX + "admission:execution-lease";
    }

    static String admitted() {
        return PREFIX + "admitted";
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

    static String admissionWaiting(Long scheduleId) {
        return schedule(scheduleId) + ":admission-waiting";
    }

    static String expiry(Long scheduleId) {
        return schedule(scheduleId) + ":expiry";
    }

    static String admitted(Long scheduleId) {
        return schedule(scheduleId) + ":admitted";
    }

    static String member(Long scheduleId, Long memberId) {
        return schedule(scheduleId) + ":member:" + memberId;
    }

    static String token(Long scheduleId, String queueToken) {
        return schedule(scheduleId) + ":token:" + queueToken;
    }
}
