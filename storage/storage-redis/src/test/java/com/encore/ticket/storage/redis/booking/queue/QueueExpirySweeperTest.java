package com.encore.ticket.storage.redis.booking.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.encore.ticket.core.booking.queue.domain.QueuePolicy;
import com.encore.ticket.core.booking.queue.port.QueueEnterResult;
import com.encore.ticket.storage.redis.support.MutableClock;
import com.encore.ticket.storage.redis.support.RedisContainerSupport;

class QueueExpirySweeperTest extends RedisContainerSupport {

    private static final QueuePolicy POLICY = QueuePolicy.DEFAULT;
    private static final long SCHEDULE_ID = 1L;
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2099-01-01T00:00:00Z");

    private static final int REQUEST_PURGE_LIMIT = 50;

    MutableClock clock;
    QueueFunctions functions;
    QueueRedisRepository repository;
    QueueExpirySweeper sweeper;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0.toInstant());
        functions = new QueueFunctions(redisTemplate);
        functions.load();
        repository = new QueueRedisRepository(redisTemplate, functions, POLICY, clock);
        sweeper = new QueueExpirySweeper(redisTemplate, functions, POLICY, clock);
    }

    @Test
    void 진입은_회차를_명단에_올린다() {
        repository.enterOrResume(SCHEDULE_ID, 100L, T0);

        assertThat(redisTemplate.opsForZSet().score("queue:schedules", "1"))
                .isEqualTo((double) T0.toInstant().toEpochMilli());
    }

    @Test
    void 요청_경로는_만료가_쌓여도_상한만큼만_걷는다() {
        int ghosts = REQUEST_PURGE_LIMIT * 3;
        enterMany(ghosts, T0);

        repository.enterOrResume(SCHEDULE_ID, 999L, expired());

        assertThat(waitingSize()).isEqualTo(ghosts - REQUEST_PURGE_LIMIT + 1);
    }

    @Test
    void 청소_담당이_돌면_남은_유령이_사라지고_순번이_맞는다() {
        enterMany(REQUEST_PURGE_LIMIT * 3, T0);
        OffsetDateTime later = expired();
        QueueEnterResult fresh = repository.enterOrResume(SCHEDULE_ID, 999L, later);

        assertThat(fresh.token().position()).isGreaterThan(1);

        sweeper.sweepAt(later);

        assertThat(waitingSize()).isEqualTo(1);
        clock.moveTo(later.toInstant());
        assertThat(repository.findByToken(SCHEDULE_ID, fresh.token().token())
                .orElseThrow().position()).isEqualTo(1);
    }

    @Test
    void 청소_담당은_한_주기에_상한을_넘겨_걷지_않는다() {
        int ghosts = 12_000;
        enterMany(ghosts, T0);
        OffsetDateTime later = expired();

        int firstRound = sweeper.sweepAt(later);

        assertThat(firstRound).isEqualTo(10_000);
        assertThat(waitingSize()).isEqualTo(ghosts - 10_000);

        sweeper.sweepAt(later);

        assertThat(waitingSize()).isZero();
    }

    @Test
    void 사람이_남아_있는_회차는_명단에_유지된다() {
        repository.enterOrResume(SCHEDULE_ID, 100L, T0);
        OffsetDateTime later = T0.plusMinutes(3);

        sweeper.sweepAt(later);

        assertThat(redisTemplate.opsForZSet().score("queue:schedules", "1"))
                .isEqualTo((double) later.toInstant().toEpochMilli());
    }

    @Test
    void 오래_조용한_회차는_명단에서_버려진다() {
        repository.enterOrResume(SCHEDULE_ID, 100L, T0);

        sweeper.sweepAt(T0.plus(POLICY.hardExpiry()).plusMinutes(2));

        assertThat(redisTemplate.opsForZSet().zCard("queue:schedules")).isZero();
        assertThat(waitingSize()).isZero();
    }

    @Test
    void 명단에_없는_회차는_건드리지_않는다() {
        assertThat(sweeper.sweepAt(T0)).isZero();
    }

    private void enterMany(int count, OffsetDateTime at) {
        for (int index = 0; index < count; index++) {
            repository.enterOrResume(SCHEDULE_ID, 1000L + index, at);
        }
    }

    private OffsetDateTime expired() {
        return T0.plus(POLICY.hardExpiry()).plusNanos(1_000_000);
    }

    private long waitingSize() {
        Long size = redisTemplate.opsForZSet().zCard("queue:{%d}:waiting".formatted(SCHEDULE_ID));
        return size == null ? 0 : size;
    }
}
