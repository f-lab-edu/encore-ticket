package com.encore.ticket.storage.redis.booking.queue;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.encore.ticket.core.booking.queue.domain.QueueAdmissionPolicy;
import com.encore.ticket.core.booking.queue.domain.QueueAuthorizationPolicy;
import com.encore.ticket.core.booking.queue.domain.QueuePolicy;

@Configuration
@EnableConfigurationProperties({QueueAdmissionProperties.class, QueueAuthorizationProperties.class})
public class QueueRedisConfig {

    @Bean
    public QueuePolicy queuePolicy() {
        return QueuePolicy.DEFAULT;
    }

    @Bean
    public QueueAuthorizationPolicy queueAuthorizationPolicy(
            QueueAuthorizationProperties properties) {
        return new QueueAuthorizationPolicy(properties.renewalWindow());
    }

    @Bean
    public QueueAdmissionPolicy queueAdmissionPolicy(
            QueueAdmissionProperties properties) {
        return new QueueAdmissionPolicy(
                properties.perScheduleCapacity(), properties.globalCapacity(),
                properties.maxPerRun(), properties.candidateScanLimit(),
                properties.waitingActivityWindow(), properties.initialLease(),
                properties.hardCap(), properties.executionLease());
    }
}
