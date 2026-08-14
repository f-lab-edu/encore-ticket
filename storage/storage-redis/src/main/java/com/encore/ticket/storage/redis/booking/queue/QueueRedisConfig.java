package com.encore.ticket.storage.redis.booking.queue;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class QueueRedisConfig {

    @Bean
    public RedisScript<List> enterOrResumeScript() {
        return listScript("scripts/enter-or-resume.lua");
    }

    @Bean
    public RedisScript<List> recordPollScript() {
        return listScript("scripts/record-poll.lua");
    }

    private RedisScript<List> listScript(String location) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(List.class);
        return script;
    }
}
