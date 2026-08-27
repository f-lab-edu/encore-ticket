package com.encore.ticket.storage.redis.booking.hold;

final class SeatHoldRedisKeys {

    private static final String PREFIX = "seat-hold:";

    private SeatHoldRedisKeys() {
    }

    static String scheduleSeats(Long scheduleId) {
        return PREFIX + "{%d}:seats".formatted(scheduleId);
    }

    static String memberSeats(Long scheduleId, Long memberId) {
        return PREFIX + "{%d}:member:%d".formatted(scheduleId, memberId);
    }

    static String seat(Long scheduleId, Long seatId) {
        return PREFIX + "{%d}:seat:%d".formatted(scheduleId, seatId);
    }

    static String hold(String holdId) {
        return PREFIX + "hold:" + holdId;
    }

    static String idempotency(Long scheduleId, Long memberId, String idempotencyKey) {
        return PREFIX + "{%d}:idem:%d:%s".formatted(scheduleId, memberId, idempotencyKey);
    }
}
