package com.encore.ticket.booking.controller;

import java.util.Set;

final class StubSchedules {

    static final long OPEN_SCHEDULE_ID = 201L;

    static final long BOOKING_CLOSED_SCHEDULE_ID = 301L;

    static final long MISSING_SCHEDULE_ID = 999L;

    private static final long CONCERT_COUNT = 10L;

    private static final Set<Long> SEQUENCES = Set.of(1L, 2L);

    private static final Set<Long> BOOKING_CLOSED_SCHEDULE_IDS = Set.of(301L, 302L);

    private StubSchedules() {
    }

    static boolean exists(long scheduleId) {
        long concertId = scheduleId / 100;
        long sequence = scheduleId % 100;

        return concertId >= 1 && concertId <= CONCERT_COUNT && SEQUENCES.contains(sequence);
    }

    static boolean bookingOpen(long scheduleId) {
        return !BOOKING_CLOSED_SCHEDULE_IDS.contains(scheduleId);
    }
}
