package com.encore.ticket.storage.redis.booking.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.encore.ticket.core.booking.dto.QueueStatus;
import com.encore.ticket.core.booking.queue.domain.QueuePolicy;
import com.encore.ticket.core.booking.queue.domain.QueueToken;
import com.encore.ticket.core.booking.queue.port.QueueEnterResult;
import com.encore.ticket.core.booking.queue.port.QueuePollOutcome;
import com.encore.ticket.core.booking.queue.port.QueuePollResult;

@Testcontainers
class QueueRedisRepositoryTest {

    private static final int REDIS_PORT = 6379;
    private static final QueuePolicy POLICY = QueuePolicy.DEFAULT;
    private static final long SCHEDULE_ID = 1L;
    private static final long MEMBER_ID = 100L;
    private static final long OTHER_MEMBER_ID = 200L;
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2099-01-01T00:00:00Z");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(REDIS_PORT);

    static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redisTemplate;

    MutableClock clock;
    QueueFunctions functions;
    QueueRedisRepository repository;

    @BeforeAll
    static void connect() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }

        clock = new MutableClock(T0.toInstant());
        functions = new QueueFunctions(redisTemplate);
        functions.load();
        repository = new QueueRedisRepository(redisTemplate, functions, POLICY, clock);
    }

    @Test
    void 서로_다른_회원의_동시_진입은_순번이_겹치지_않는다() throws Exception {
        int members = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<QueueEnterResult> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(members)) {
            List<Future<QueueEnterResult>> futures = new ArrayList<>();
            for (int index = 0; index < members; index++) {
                long memberId = 100L + index;
                futures.add(executor.submit(() -> {
                    start.await();
                    return repository.enterOrResume(SCHEDULE_ID, memberId, T0);
                }));
            }
            start.countDown();
            for (Future<QueueEnterResult> future : futures) {
                results.add(future.get());
            }
        }

        assertThat(results).allMatch(QueueEnterResult::created);
        assertThat(results).extracting(result -> result.token().token())
                .doesNotHaveDuplicates();
        assertThat(results).extracting(result -> result.token().sequence())
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(results).extracting(result -> result.token().position())
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(waitingSize()).isEqualTo(members);
    }

    @Test
    void 같은_회원의_동시_진입은_하나만_새_토큰을_만든다() throws Exception {
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<QueueEnterResult> first = executor.submit(() -> {
                start.await();
                return repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);
            });
            Future<QueueEnterResult> second = executor.submit(() -> {
                start.await();
                return repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);
            });
            start.countDown();

            QueueEnterResult left = first.get();
            QueueEnterResult right = second.get();

            assertThat(List.of(left.created(), right.created()))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(left.token().token()).isEqualTo(right.token().token());
            assertThat(left.token().sequence()).isEqualTo(right.token().sequence());
        }

        assertThat(waitingSize()).isEqualTo(1);
    }

    @Test
    void 진입이_만든_키는_모두_같은_시각에_만료된다() {
        QueueEnterResult entered = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);

        long deadline = T0.plus(POLICY.hardExpiry()).toInstant().toEpochMilli() + 1_000L;

        assertThat(physicalExpireAt(tokenKey(entered.token().token())))
                .isCloseTo(deadline, within(2_000L));
        assertThat(physicalExpireAt(memberKey(MEMBER_ID)))
                .isCloseTo(deadline, within(2_000L));
        assertThat(physicalExpireAt(waitingKey())).isCloseTo(deadline, within(2_000L));
    }

    @Test
    void 정확히_5분_뒤_재진입은_유예를_쓰지_않는다() {
        QueueEnterResult first = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);

        QueueEnterResult again = repository.enterOrResume(
                SCHEDULE_ID, MEMBER_ID, T0.plusMinutes(5));

        assertThat(again.created()).isFalse();
        assertThat(again.token().token()).isEqualTo(first.token().token());
        assertThat(again.token().sequence()).isEqualTo(first.token().sequence());
        assertThat(again.token().lapsesRemaining()).isEqualTo(POLICY.maxLapses());
    }

    @Test
    void 유예_5분을_넘겨_재진입하면_유예를_한_번_쓴다() {
        repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);

        QueueEnterResult again = repository.enterOrResume(
                SCHEDULE_ID, MEMBER_ID, T0.plusMinutes(5).plusNanos(1_000_000));

        assertThat(again.created()).isFalse();
        assertThat(again.token().lapsesRemaining()).isEqualTo(1);
    }

    @Test
    void 정확히_10분_뒤_재진입은_유예를_한_번만_쓴다() {
        QueueEnterResult first = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);

        QueueEnterResult again = repository.enterOrResume(
                SCHEDULE_ID, MEMBER_ID, T0.plusMinutes(10));

        assertThat(again.created()).isFalse();
        assertThat(again.token().token()).isEqualTo(first.token().token());
        assertThat(again.token().lapsesRemaining()).isEqualTo(1);
    }

    @Test
    void 정확히_15분_뒤_재진입은_토큰을_유지하고_남은_유예를_모두_쓴다() {
        QueueEnterResult first = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);

        QueueEnterResult again = repository.enterOrResume(
                SCHEDULE_ID, MEMBER_ID, T0.plusMinutes(15));

        assertThat(again.created()).isFalse();
        assertThat(again.token().token()).isEqualTo(first.token().token());
        assertThat(again.token().sequence()).isEqualTo(first.token().sequence());
        assertThat(again.token().lapsesRemaining()).isZero();
    }

    @Test
    void 한계_15분을_넘겨_재진입하면_새_토큰과_새_순번을_받는다() {
        QueueEnterResult first = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);

        QueueEnterResult again = repository.enterOrResume(
                SCHEDULE_ID, MEMBER_ID, T0.plusMinutes(15).plusNanos(1_000_000));

        assertThat(again.created()).isTrue();
        assertThat(again.token().token()).isNotEqualTo(first.token().token());
        assertThat(again.token().sequence()).isEqualTo(2);
        assertThat(again.token().position()).isEqualTo(1);
        assertThat(again.token().lapsesRemaining()).isEqualTo(POLICY.maxLapses());

        assertThat(redisTemplate.hasKey(tokenKey(first.token().token()))).isFalse();
        assertThat(waitingMembers()).containsExactly(again.token().token());
        assertThat(waitingSize()).isEqualTo(1);
    }

    @Test
    void 만료된_토큰은_뒤에_온_회원의_순번에_들어가지_않는다() {
        repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);

        QueueEnterResult later = repository.enterOrResume(
                SCHEDULE_ID, OTHER_MEMBER_ID, T0.plusMinutes(15).plusNanos(1_000_000));

        assertThat(later.created()).isTrue();
        assertThat(later.token().position()).isEqualTo(1);
        assertThat(later.token().sequence()).isEqualTo(2);
        assertThat(waitingSize()).isEqualTo(1);
        assertThat(redisTemplate.hasKey(memberKey(MEMBER_ID))).isFalse();
    }

    @Test
    void 앞_사람이_만료되면_뒤_사람의_순번이_당겨진다() {
        repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);
        QueueEnterResult second = repository.enterOrResume(SCHEDULE_ID, OTHER_MEMBER_ID, T0);
        assertThat(second.token().position()).isEqualTo(2);

        repository.recordPoll(
                SCHEDULE_ID, second.token().token(), OTHER_MEMBER_ID, T0.plusMinutes(10));
        QueuePollResult poll = repository.recordPoll(
                SCHEDULE_ID, second.token().token(), OTHER_MEMBER_ID,
                T0.plusMinutes(15).plusNanos(1_000_000));

        assertThat(poll.outcome()).isEqualTo(QueuePollOutcome.UPDATED);
        assertThat(poll.token().position()).isEqualTo(1);
        assertThat(poll.token().sequence()).isEqualTo(2);
        assertThat(waitingSize()).isEqualTo(1);
    }

    @Test
    void 상태_조회는_마지막_폴링_시각과_TTL을_갱신한다() {
        QueueEnterResult entered = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);
        OffsetDateTime polledAt = T0.plusMinutes(4);

        QueuePollResult result = repository.recordPoll(
                SCHEDULE_ID, entered.token().token(), MEMBER_ID, polledAt);

        assertThat(result.outcome()).isEqualTo(QueuePollOutcome.UPDATED);
        assertThat(result.token().lastPolledAt()).isEqualTo(polledAt);
        assertThat(result.token().lapsesRemaining()).isEqualTo(POLICY.maxLapses());
        assertThat(result.token().position()).isEqualTo(1);

        long hardExpiresAt = polledAt.plus(POLICY.hardExpiry()).toInstant().toEpochMilli();
        assertThat(hashField(entered.token().token(), "hardExpiresAt"))
                .isEqualTo(String.valueOf(hardExpiresAt));
        assertThat(physicalExpireAt(tokenKey(entered.token().token())))
                .isCloseTo(hardExpiresAt + 1_000L, within(2_000L));
        assertThat(physicalExpireAt(memberKey(MEMBER_ID)))
                .isCloseTo(hardExpiresAt + 1_000L, within(2_000L));
    }

    @Test
    void 상태_조회도_유예를_소비한다() {
        QueueEnterResult entered = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);

        QueuePollResult result = repository.recordPoll(
                SCHEDULE_ID, entered.token().token(), MEMBER_ID, T0.plusMinutes(10));

        assertThat(result.outcome()).isEqualTo(QueuePollOutcome.UPDATED);
        assertThat(result.token().lapsesRemaining()).isEqualTo(1);
    }

    @Test
    void 남의_토큰으로_상태를_조회하면_아무것도_갱신하지_않는다() {
        QueueEnterResult entered = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);

        QueuePollResult result = repository.recordPoll(
                SCHEDULE_ID, entered.token().token(), OTHER_MEMBER_ID, T0.plusMinutes(4));

        assertThat(result.outcome()).isEqualTo(QueuePollOutcome.NOT_OWNED);
        assertThat(result.token()).isNull();
        assertThat(hashField(entered.token().token(), "lastPolledAt"))
                .isEqualTo(String.valueOf(T0.toInstant().toEpochMilli()));
    }

    @Test
    void 없는_토큰의_상태_조회는_찾지_못한다() {
        QueuePollResult result = repository.recordPoll(
                SCHEDULE_ID, "q_none", MEMBER_ID, T0);

        assertThat(result.outcome()).isEqualTo(QueuePollOutcome.NOT_FOUND);
    }

    @Test
    void 한계_15분을_넘긴_토큰의_상태_조회는_만료이고_인덱스를_비운다() {
        QueueEnterResult entered = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);

        QueuePollResult result = repository.recordPoll(
                SCHEDULE_ID, entered.token().token(), MEMBER_ID,
                T0.plusMinutes(15).plusNanos(1_000_000));

        assertThat(result.outcome()).isEqualTo(QueuePollOutcome.EXPIRED);
        assertThat(redisTemplate.hasKey(tokenKey(entered.token().token()))).isFalse();
        assertThat(redisTemplate.hasKey(memberKey(MEMBER_ID))).isFalse();
        assertThat(waitingSize()).isZero();
    }

    @Test
    void 소유자_검사는_만료_검사보다_먼저다() {
        QueueEnterResult entered = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);

        QueuePollResult result = repository.recordPoll(
                SCHEDULE_ID, entered.token().token(), OTHER_MEMBER_ID,
                T0.plusMinutes(15).plusNanos(1_000_000));

        assertThat(result.outcome()).isEqualTo(QueuePollOutcome.NOT_OWNED);
        assertThat(redisTemplate.hasKey(tokenKey(entered.token().token()))).isTrue();
    }

    @Test
    void 토큰으로_대기_상태를_복원한다() {
        QueueEnterResult entered = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);
        clock.moveTo(T0.plusMinutes(1).toInstant());

        QueueToken found = repository
                .findByToken(SCHEDULE_ID, entered.token().token())
                .orElseThrow();

        assertThat(found.memberId()).isEqualTo(MEMBER_ID);
        assertThat(found.scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(found.position()).isEqualTo(1);
        assertThat(found.sequence()).isEqualTo(1);
        assertThat(found.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(found.lastPolledAt()).isEqualTo(T0);
        assertThat(found.lapsesRemaining()).isEqualTo(POLICY.maxLapses());
        assertThat(found.admittedUntil()).isNull();
    }

    @Test
    void 한계_15분을_넘긴_토큰은_조회되지_않는다() {
        QueueEnterResult entered = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);
        clock.moveTo(T0.plusMinutes(15).plusNanos(1_000_000).toInstant());

        assertThat(repository.findByToken(SCHEDULE_ID, entered.token().token())).isEmpty();
    }

    @Test
    void 입장_허용_토큰을_만나면_상태_조회가_명시적으로_실패한다() {
        String token = admitToken();

        assertThatThrownBy(() -> repository.recordPoll(
                SCHEDULE_ID, token, MEMBER_ID, T0.plusMinutes(12)))
                .hasMessageContaining("ADMITTED_NOT_SUPPORTED");
    }

    @Test
    void 입장_허용_토큰을_만나면_재진입이_명시적으로_실패한다() {
        admitToken();

        assertThatThrownBy(() -> repository.enterOrResume(
                SCHEDULE_ID, MEMBER_ID, T0.plusMinutes(1)))
                .hasMessageContaining("ADMITTED_NOT_SUPPORTED");
    }

    private String admitToken() {
        QueueEnterResult entered = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);
        String token = entered.token().token();
        redisTemplate.opsForHash().putAll(tokenKey(token), Map.of(
                "status", QueueStatus.ADMITTED.name(),
                "admittedUntil", String.valueOf(T0.plusMinutes(30).toInstant().toEpochMilli())));
        redisTemplate.opsForZSet().remove(waitingKey(), token);
        return token;
    }

    @Test
    void 회차가_다르면_순번을_따로_센다() {
        QueueEnterResult first = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);
        QueueEnterResult other = repository.enterOrResume(2L, MEMBER_ID, T0);

        assertThat(first.token().sequence()).isEqualTo(1);
        assertThat(other.token().sequence()).isEqualTo(1);
        assertThat(first.token().token()).isNotEqualTo(other.token().token());
    }

    private String waitingKey() {
        return "queue:{%d}:waiting".formatted(SCHEDULE_ID);
    }

    private String tokenKey(String token) {
        return "queue:{%d}:token:%s".formatted(SCHEDULE_ID, token);
    }

    private String memberKey(long memberId) {
        return "queue:{%d}:member:%d".formatted(SCHEDULE_ID, memberId);
    }

    private long waitingSize() {
        Long size = redisTemplate.opsForZSet().zCard(waitingKey());
        return size == null ? 0 : size;
    }

    private Set<String> waitingMembers() {
        return redisTemplate.opsForZSet().range(waitingKey(), 0, -1);
    }

    private long physicalExpireAt(String key) {
        Long ttlMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        assertThat(ttlMillis).isNotNull().isPositive();
        return System.currentTimeMillis() + ttlMillis;
    }

    private String hashField(String token, String field) {
        Object value = redisTemplate.opsForHash().get(tokenKey(token), field);
        return value == null ? null : value.toString();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void moveTo(Instant target) {
            this.instant = target;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
