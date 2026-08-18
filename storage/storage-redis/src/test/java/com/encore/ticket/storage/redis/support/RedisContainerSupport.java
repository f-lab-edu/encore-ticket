package com.encore.ticket.storage.redis.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class RedisContainerSupport {

    private static final int REDIS_PORT = 6379;

    protected static LettuceConnectionFactory connectionFactory;
    protected static StringRedisTemplate redisTemplate;

    static {
        GenericContainer<?> redis = new GenericContainer<>(
                DockerImageName.parse("redis:7.4-alpine"))
                .withExposedPorts(REDIS_PORT);
        redis.start();

        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(REDIS_PORT)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @BeforeEach
    protected void flushRedis() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }
}
