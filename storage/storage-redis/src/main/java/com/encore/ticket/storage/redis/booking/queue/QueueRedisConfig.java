package com.encore.ticket.storage.redis.booking.queue;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.encore.ticket.core.booking.queue.domain.QueuePolicy;

@Configuration
public class QueueRedisConfig {

    @Bean
    public QueuePolicy queuePolicy() {
        return QueuePolicy.DEFAULT;
    }
}
