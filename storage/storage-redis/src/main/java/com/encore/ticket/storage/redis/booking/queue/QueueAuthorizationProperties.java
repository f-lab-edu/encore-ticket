package com.encore.ticket.storage.redis.booking.queue;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ticket.queue.authorization")
public record QueueAuthorizationProperties(
        @DefaultValue("5m") Duration renewalWindow) {
}
