package com.encore.ticket.core.booking.queue.port;

public record QueueAdmissionResult(boolean executionLeaseAcquired, int admittedCount) {

    public static QueueAdmissionResult leaseNotAcquired() {
        return new QueueAdmissionResult(false, 0);
    }

    public static QueueAdmissionResult completed(int admittedCount) {
        return new QueueAdmissionResult(true, admittedCount);
    }
}
