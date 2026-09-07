package com.encore.ticket.storage.redis.booking.queue;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ticket.queue.admission")
public record QueueAdmissionProperties(
        @DefaultValue("100") int perScheduleCapacity,
        @DefaultValue("500") int globalCapacity,
        @DefaultValue("100") int maxPerRun,
        @DefaultValue("1000") int candidateScanLimit,
        @DefaultValue("5m") Duration waitingActivityWindow,
        @DefaultValue("5m") Duration initialLease,
        @DefaultValue("30m") Duration hardCap,
        @DefaultValue("900ms") Duration executionLease) {
}
