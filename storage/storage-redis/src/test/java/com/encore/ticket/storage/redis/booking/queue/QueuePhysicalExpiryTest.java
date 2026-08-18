package com.encore.ticket.storage.redis.booking.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.encore.ticket.core.booking.queue.domain.QueuePolicy;
import com.encore.ticket.core.booking.queue.port.QueueEnterResult;
import com.encore.ticket.storage.redis.support.RedisContainerSupport;

class QueuePhysicalExpiryTest extends RedisContainerSupport {

    private static final long SCHEDULE_ID = 1L;
    private static final long MEMBER_ID = 100L;

    private static final QueuePolicy SHORT = new QueuePolicy(Duration.ofMillis(100), 2);

    QueueRedisRepository repository;

    @BeforeEach
    void setUp() {
        QueueFunctions functions = new QueueFunctions(redisTemplate);
        functions.load();
        repository = new QueueRedisRepository(
                redisTemplate, functions, SHORT, Clock.systemUTC());
    }

    @Test
    void 수명이_지나면_Redis_가_토큰과_회원_매핑을_스스로_지운다() {
        QueueEnterResult entered = repository.enterOrResume(
                SCHEDULE_ID, MEMBER_ID, OffsetDateTime.now(Clock.systemUTC()));
        String token = entered.token().token();

        assertThat(redisTemplate.hasKey(tokenKey(token))).isTrue();
        assertThat(redisTemplate.hasKey(memberKey())).isTrue();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(redisTemplate.hasKey(tokenKey(token))).isFalse();
            assertThat(redisTemplate.hasKey(memberKey())).isFalse();
        });
    }

    @Test
    void 줄이_다_비면_순번은_다시_1부터_센다() {
        QueueEnterResult first = repository.enterOrResume(
                SCHEDULE_ID, MEMBER_ID, OffsetDateTime.now(Clock.systemUTC()));

        await().atMost(Duration.ofSeconds(5))
                .until(() -> !redisTemplate.hasKey(memberKey()));

        QueueEnterResult again = repository.enterOrResume(
                SCHEDULE_ID, MEMBER_ID, OffsetDateTime.now(Clock.systemUTC()));

        assertThat(again.created()).isTrue();
        assertThat(again.token().token()).isNotEqualTo(first.token().token());

        assertThat(again.token().sequence()).isEqualTo(1);
        assertThat(again.token().position()).isEqualTo(1);
    }

    @Test
    void 살아_있는_토큰이_있으면_순번_카운터가_먼저_사라지지_않는다() {
        Clock realClock = Clock.systemUTC();
        repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, OffsetDateTime.now(realClock));

        for (int round = 0; round < 5; round++) {
            QueueEnterResult alive = repository.enterOrResume(
                    SCHEDULE_ID, MEMBER_ID, OffsetDateTime.now(realClock));
            assertThat(alive.created()).isFalse();
            sleepMillis(50);
        }

        QueueEnterResult later = repository.enterOrResume(
                SCHEDULE_ID, 200L, OffsetDateTime.now(realClock));

        assertThat(later.token().sequence()).isEqualTo(2);
        assertThat(later.token().position()).isEqualTo(2);
    }

    private void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 대기_줄도_마지막_토큰보다_오래_남지_않는다() {
        repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, OffsetDateTime.now(Clock.systemUTC()));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(redisTemplate.hasKey("queue:{%d}:waiting".formatted(SCHEDULE_ID))).isFalse();
            assertThat(redisTemplate.hasKey("queue:{%d}:expiry".formatted(SCHEDULE_ID))).isFalse();
        });
    }

    private String tokenKey(String token) {
        return "queue:{%d}:token:%s".formatted(SCHEDULE_ID, token);
    }

    private String memberKey() {
        return "queue:{%d}:member:%d".formatted(SCHEDULE_ID, MEMBER_ID);
    }
}
