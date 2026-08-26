package com.encore.ticket.storage.redis.booking.queue;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.encore.ticket.core.booking.queue.domain.QueueAdmissionPolicy;
import com.encore.ticket.core.booking.queue.domain.QueuePolicy;

@Configuration
public class QueueRedisConfig {

    @Bean
    public QueuePolicy queuePolicy() {
        return QueuePolicy.DEFAULT;
    }

    @Bean
    public QueueAdmissionPolicy queueAdmissionPolicy(
            @Value("${ticket.queue.admission.per-schedule-capacity:100}") int perScheduleCapacity,
            @Value("${ticket.queue.admission.global-capacity:500}") int globalCapacity,
            @Value("${ticket.queue.admission.max-per-run:100}") int maxAdmissionsPerRun,
            @Value("${ticket.queue.admission.candidate-scan-limit:1000}") int candidateScanLimit,
            @Value("${ticket.queue.admission.waiting-activity-window:5m}") Duration waitingActivityWindow,
            @Value("${ticket.queue.admission.initial-lease:5m}") Duration initialLease,
            @Value("${ticket.queue.admission.hard-cap:30m}") Duration hardCap,
            @Value("${ticket.queue.admission.execution-lease:900ms}") Duration executionLease) {
        return new QueueAdmissionPolicy(
                perScheduleCapacity, globalCapacity, maxAdmissionsPerRun, candidateScanLimit,
                waitingActivityWindow, initialLease, hardCap, executionLease);
    }
}
