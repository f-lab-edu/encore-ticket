package com.encore.ticket.storage.redis.booking.hold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.encore.ticket.core.booking.hold.domain.SeatHold;
import com.encore.ticket.core.booking.hold.port.SeatHoldAcquireResult;
import com.encore.ticket.core.booking.hold.port.SeatHoldAcquisition;
import com.encore.ticket.core.booking.reservation.domain.HeldSeats;
import com.encore.ticket.storage.redis.support.RedisContainerSupport;

class SeatHoldRedisRepositoryTest extends RedisContainerSupport {

    private static final Clock CLOCK = Clock.systemUTC();

    SeatHoldRedisRepository repository;

    @BeforeEach
    void setUp() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/acquire-seat-hold.lua"));
        script.setResultType(List.class);
        repository = new SeatHoldRedisRepository(redisTemplate, script, CLOCK);
    }

    @Test
    void 여러_좌석을_한번에_선점한다() {
        SeatHold hold = hold(
                "hold_A", 1L, List.of(11L, 12L), 100L, Duration.ofSeconds(30));

        SeatHoldAcquireResult result = acquire(hold, 4);

        assertThat(result).isEqualTo(SeatHoldAcquireResult.ACQUIRED);
        assertThat(repository.holdExpiryBySeatId(1L)).containsOnlyKeys(11L, 12L);
    }

    @Test
    void 구매_제한과_같은_네_좌석은_선점한다() {
        SeatHoldAcquireResult result = acquire(
                hold("hold_A", 1L, List.of(11L, 12L, 13L, 14L), 100L,
                        Duration.ofSeconds(30)), 4);

        assertThat(result).isEqualTo(SeatHoldAcquireResult.ACQUIRED);
        assertThat(repository.holdExpiryBySeatId(1L))
                .containsOnlyKeys(11L, 12L, 13L, 14L);
    }

    @Test
    void 한_좌석이라도_겹치면_새_요청은_하나도_저장하지_않는다() {
        acquire(
                hold("hold_A", 1L, List.of(12L), 100L, Duration.ofSeconds(30)), 4);

        SeatHoldAcquireResult result = acquire(
                hold("hold_B", 1L, List.of(11L, 12L, 13L), 200L,
                        Duration.ofSeconds(30)), 4);

        assertThat(result).isEqualTo(SeatHoldAcquireResult.SEAT_ALREADY_HELD);
        assertThat(repository.holdExpiryBySeatId(1L)).containsOnlyKeys(12L);
        assertThat(repository.findByHoldId("hold_B")).isEmpty();
    }

    @Test
    void 회원의_활성_좌석이_구매_제한을_넘으면_저장하지_않는다() {
        acquire(
                hold("hold_A", 1L, List.of(11L, 12L, 13L), 100L,
                        Duration.ofSeconds(30)), 4);

        SeatHoldAcquireResult result = acquire(
                hold("hold_B", 1L, List.of(14L, 15L), 100L,
                        Duration.ofSeconds(30)), 4);

        assertThat(result).isEqualTo(SeatHoldAcquireResult.PURCHASE_LIMIT_EXCEEDED);
        assertThat(repository.holdExpiryBySeatId(1L))
                .containsOnlyKeys(11L, 12L, 13L);
    }

    @Test
    void holdId로_선점_정보를_복원한다() {
        SeatHold hold = hold(
                "hold_A", 1L, List.of(11L, 12L), 100L, Duration.ofSeconds(30));
        acquire(hold, 4);

        HeldSeats found = repository.findByHoldId("hold_A").orElseThrow();

        assertThat(found.scheduleId()).isEqualTo(1L);
        assertThat(found.seatIds()).containsExactly(11L, 12L);
        assertThat(found.memberId()).isEqualTo(100L);
        assertThat(found.expiresAt()).isEqualTo(hold.expiresAt());
    }

    @Test
    void 같은_좌석의_동시_요청은_하나만_성공한다() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<SeatHoldAcquireResult> first = executor.submit(() -> {
                start.await();
                return acquire(
                        hold("hold_A", 1L, List.of(12L), 100L,
                                Duration.ofSeconds(30)), 4);
            });
            Future<SeatHoldAcquireResult> second = executor.submit(() -> {
                start.await();
                return acquire(
                        hold("hold_B", 1L, List.of(12L), 200L,
                                Duration.ofSeconds(30)), 4);
            });

            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(
                            SeatHoldAcquireResult.ACQUIRED,
                            SeatHoldAcquireResult.SEAT_ALREADY_HELD);
        }
    }

    @Test
    void 만료된_선점은_조회되지_않는다() {
        acquire(
                hold("hold_short", 1L, List.of(12L), 100L,
                        Duration.ofMillis(300)), 4);

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(repository.findByHoldId("hold_short")).isEmpty();
            assertThat(repository.holdExpiryBySeatId(1L)).isEqualTo(Map.of());
        });
    }

    @Test
    void 만료된_좌석은_회원의_구매_제한에서_제외한다() {
        acquire(
                hold("hold_short", 1L, List.of(11L, 12L, 13L, 14L), 100L,
                        Duration.ofMillis(300)), 4);

        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(
                        repository.findByHoldId("hold_short")).isEmpty());

        SeatHoldAcquireResult result = acquire(
                hold("hold_next", 1L, List.of(21L, 22L, 23L, 24L), 100L,
                        Duration.ofSeconds(30)), 4);

        assertThat(result).isEqualTo(SeatHoldAcquireResult.ACQUIRED);
        assertThat(repository.holdExpiryBySeatId(1L))
                .containsOnlyKeys(21L, 22L, 23L, 24L);
    }

    @Test
    void 같은_키로_같은_요청이_다시_오면_최초_선점을_그대로_돌려준다() {
        SeatHold first = hold("hold_A", 1L, List.of(11L, 12L), 100L, Duration.ofSeconds(30));
        acquire(first, 4, "idem-1");

        SeatHold retried = hold("hold_B", 1L, List.of(11L, 12L), 100L, Duration.ofSeconds(30));
        SeatHoldAcquisition replayed = acquire(retried, 4, "idem-1");

        assertThat(replayed.result()).isEqualTo(SeatHoldAcquireResult.REPLAYED);
        assertThat(replayed.holdId()).isEqualTo("hold_A");
        assertThat(replayed.expiresAt()).isEqualTo(first.expiresAt());
        assertThat(repository.findByHoldId("hold_B")).isEmpty();
        assertThat(repository.holdExpiryBySeatId(1L)).containsOnlyKeys(11L, 12L);
    }

    @Test
    void 같은_키로_다른_요청이_오면_키_재사용으로_거절한다() {
        acquire(hold("hold_A", 1L, List.of(11L), 100L, Duration.ofSeconds(30)), 4, "idem-1");

        SeatHoldAcquisition reused = acquire(
                hold("hold_B", 1L, List.of(12L), 100L, Duration.ofSeconds(30)), 4, "idem-1");

        assertThat(reused.result()).isEqualTo(SeatHoldAcquireResult.IDEMPOTENCY_KEY_REUSED);
        assertThat(repository.holdExpiryBySeatId(1L)).containsOnlyKeys(11L);
        assertThat(repository.findByHoldId("hold_B")).isEmpty();
    }

    @Test
    void 멱등성_키는_회원마다_독립이다() {
        acquire(hold("hold_A", 1L, List.of(11L), 100L, Duration.ofSeconds(30)), 4, "idem-1");

        SeatHoldAcquisition other = acquire(
                hold("hold_B", 1L, List.of(12L), 200L, Duration.ofSeconds(30)), 4, "idem-1");

        assertThat(other.result()).isEqualTo(SeatHoldAcquireResult.ACQUIRED);
        assertThat(repository.holdExpiryBySeatId(1L)).containsOnlyKeys(11L, 12L);
    }

    private SeatHoldAcquireResult acquire(SeatHold hold, int maxSeats) {
        return acquire(hold, maxSeats, "idem-" + hold.holdId()).result();
    }

    private SeatHoldAcquisition acquire(SeatHold hold, int maxSeats, String idempotencyKey) {
        return repository.acquire(hold, maxSeats, idempotencyKey, fingerprintOf(hold));
    }

    private static String fingerprintOf(SeatHold hold) {
        return hold.scheduleId() + ":" + hold.seatIds().stream()
                .sorted()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private SeatHold hold(
            String holdId,
            Long scheduleId,
            List<Long> seatIds,
            Long memberId,
            Duration ttl) {
        OffsetDateTime expiresAt = OffsetDateTime.now(CLOCK)
                .plus(ttl)
                .truncatedTo(ChronoUnit.MILLIS)
                .withOffsetSameInstant(ZoneOffset.UTC);
        return new SeatHold(holdId, scheduleId, seatIds, memberId, expiresAt);
    }
}
