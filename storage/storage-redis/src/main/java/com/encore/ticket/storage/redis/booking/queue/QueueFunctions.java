package com.encore.ticket.storage.redis.booking.queue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.async.RedisAsyncCommands;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class QueueFunctions implements InitializingBean {

    static final String ENTER_OR_RESUME = "queue_enter_or_resume";
    static final String RECORD_POLL = "queue_record_poll";
    static final String SWEEP_EXPIRED = "queue_sweep_expired";
    static final String ADMIT = "queue_admit";
    static final String RELEASE_ADMISSION_LEASE = "queue_release_admission_lease";

    private static final String LIBRARY = "scripts/queue.lua";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void afterPropertiesSet() {
        load();
    }

    public void load() {
        redisTemplate.execute((RedisCallback<Object>) connection -> connection.execute(
                "FUNCTION", bytes("LOAD"), bytes("REPLACE"), source()));
    }

    Map<String, String> call(String function, List<String> keys, String... args) {
        List<Object> reply = redisTemplate.execute((RedisCallback<List<Object>>) connection -> {
            try {
                return commands(connection).<List<Object>>fcall(
                        function, ScriptOutputType.MULTI, toBytes(keys), toBytes(args)).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Redis 대기열 호출이 중단되었습니다: " + function, e);
            } catch (ExecutionException e) {
                throw rethrow(function, e);
            }
        });

        if (reply == null || reply.isEmpty()) {
            throw new IllegalStateException("Redis 대기열 함수가 결과를 반환하지 않았습니다: " + function);
        }
        return toMap(reply);
    }

    @SuppressWarnings("unchecked")
    private RedisAsyncCommands<byte[], byte[]> commands(
            org.springframework.data.redis.connection.RedisConnection connection) {
        Object nativeConnection = connection.getNativeConnection();
        if (nativeConnection instanceof RedisAsyncCommands<?, ?> commands) {
            return (RedisAsyncCommands<byte[], byte[]>) commands;
        }
        throw new IllegalStateException(
                "대기열은 Lettuce 연결을 전제한다: " + nativeConnection.getClass().getName());
    }

    private RuntimeException rethrow(String function, ExecutionException e) {
        if (e.getCause() instanceof RuntimeException cause) {
            return cause;
        }
        return new IllegalStateException("Redis 대기열 호출이 실패했습니다: " + function, e.getCause());
    }

    private Map<String, String> toMap(List<Object> reply) {
        if (reply.size() % 2 != 0) {
            throw new IllegalStateException("이름과 값의 짝이 맞지 않습니다: " + reply.size());
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (int index = 0; index < reply.size(); index += 2) {
            fields.put(decode(reply.get(index)), decode(reply.get(index + 1)));
        }
        return fields;
    }

    private String decode(Object value) {
        if (value instanceof byte[] raw) {
            return new String(raw, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private byte[] source() {
        try {
            return new ClassPathResource(LIBRARY).getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("대기열 함수 파일을 읽지 못했습니다: " + LIBRARY, e);
        }
    }

    private byte[][] toBytes(List<String> values) {
        List<byte[]> encoded = new ArrayList<>(values.size());
        values.forEach(value -> encoded.add(bytes(value)));
        return encoded.toArray(byte[][]::new);
    }

    private byte[][] toBytes(String[] values) {
        byte[][] encoded = new byte[values.length][];
        for (int index = 0; index < values.length; index++) {
            encoded[index] = bytes(values[index]);
        }
        return encoded;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
