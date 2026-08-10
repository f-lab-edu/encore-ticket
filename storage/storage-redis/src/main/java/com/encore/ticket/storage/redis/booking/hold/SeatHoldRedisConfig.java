package com.encore.ticket.storage.redis.booking.hold;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class SeatHoldRedisConfig {

    @Bean
    public RedisScript<Long> acquireSeatHoldScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/acquire-seat-hold.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
