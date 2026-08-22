package com.encore.ticket.core.booking.queue.domain;

import java.time.Duration;

public record QueueAdmissionPolicy(
        int perScheduleCapacity,
        int globalCapacity,
        int maxAdmissionsPerRun,
        int candidateScanLimit,
        Duration waitingActivityWindow,
        Duration initialLease,
        Duration hardCap,
        Duration executionLease) {

    public QueueAdmissionPolicy {
        if (perScheduleCapacity <= 0 || globalCapacity <= 0 || maxAdmissionsPerRun <= 0
                || candidateScanLimit <= 0) {
            throw new IllegalArgumentException("대기열 Admission 제한은 0보다 커야 합니다.");
        }
        if (perScheduleCapacity > globalCapacity) {
            throw new IllegalArgumentException("회차별 수용량은 전체 수용량보다 클 수 없습니다.");
        }
        if (waitingActivityWindow.isZero() || waitingActivityWindow.isNegative()
                || initialLease.isZero() || initialLease.isNegative()
                || hardCap.isZero() || hardCap.isNegative()
                || executionLease.isZero() || executionLease.isNegative()) {
            throw new IllegalArgumentException("대기열 Admission 시간 설정은 0보다 길어야 합니다.");
        }
        if (initialLease.compareTo(hardCap) > 0) {
            throw new IllegalArgumentException("최초 lease는 hard cap보다 길 수 없습니다.");
        }
    }
}
