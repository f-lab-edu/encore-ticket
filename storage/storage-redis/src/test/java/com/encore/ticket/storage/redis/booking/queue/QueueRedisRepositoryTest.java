package com.encore.ticket.storage.redis.booking.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.encore.ticket.core.booking.dto.QueueStatus;
import com.encore.ticket.core.booking.queue.domain.QueueAdmissionPolicy;
import com.encore.ticket.core.booking.queue.domain.QueuePolicy;
import com.encore.ticket.core.booking.queue.domain.QueueToken;
import com.encore.ticket.core.booking.queue.port.QueueAdmissionResult;
import com.encore.ticket.core.booking.queue.port.QueueEnterResult;
import com.encore.ticket.core.booking.queue.port.QueuePollOutcome;
import com.encore.ticket.core.booking.queue.port.QueuePollResult;
import com.encore.ticket.storage.redis.support.MutableClock;
import com.encore.ticket.storage.redis.support.RedisContainerSupport;

class QueueRedisRepositoryTest extends RedisContainerSupport {

    private static final QueuePolicy POLICY = QueuePolicy.DEFAULT;
    private static final QueueAdmissionPolicy ADMISSION_POLICY = admissionPolicy(100, 500, 100);
    private static final long SCHEDULE_ID = 1L;
    private static final long MEMBER_ID = 100L;
    private static final long OTHER_MEMBER_ID = 200L;
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2099-01-01T00:00:00Z");

    MutableClock clock;
    QueueFunctions functions;
    QueueRedisRepository repository;

    @BeforeEach
    void setUp() {
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
    void WAITING_생성과_Admission_회차_등록은_같은_Function에서_완료된다() {
        repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);

        assertThat(waitingSize()).isEqualTo(1);
        assertThat(admissionSchedules()).contains(String.valueOf(SCHEDULE_ID));
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
    void 입장_허용_토큰은_최초_lease_동안_조회할_수_있고_조회로_연장되지_않는다() {
        QueueEnterResult entered = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);
        repository.admit(T0, ADMISSION_POLICY);

        QueuePollResult result = repository.recordPoll(
                SCHEDULE_ID, entered.token().token(), MEMBER_ID, T0.plusMinutes(4));

        assertThat(result.outcome()).isEqualTo(QueuePollOutcome.UPDATED);
        assertThat(result.token().status()).isEqualTo(QueueStatus.ADMITTED);
        assertThat(result.token().admittedUntil()).isEqualTo(T0.plusMinutes(5));
        assertThat(hashField(entered.token().token(), "admittedUntil"))
                .isEqualTo(String.valueOf(T0.plusMinutes(5).toInstant().toEpochMilli()));
    }

    @Test
    void 입장_허용_토큰으로_재진입하면_같은_토큰을_유지한다() {
        QueueEnterResult entered = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);
        repository.admit(T0, ADMISSION_POLICY);

        QueueEnterResult resumed = repository.enterOrResume(
                SCHEDULE_ID, MEMBER_ID, T0.plusMinutes(1));

        assertThat(resumed.created()).isFalse();
        assertThat(resumed.token().token()).isEqualTo(entered.token().token());
        assertThat(resumed.token().status()).isEqualTo(QueueStatus.ADMITTED);
        assertThat(resumed.token().admittedUntil()).isEqualTo(T0.plusMinutes(5));
    }

    @Test
    void 최근에_polling한_WAITING만_FIFO로_입장시킨다() {
        QueueEnterResult inactive = repository.enterOrResume(SCHEDULE_ID, 100L, T0);
        QueueEnterResult firstActive = repository.enterOrResume(SCHEDULE_ID, 200L, T0);
        QueueEnterResult secondActive = repository.enterOrResume(SCHEDULE_ID, 300L, T0);
        OffsetDateTime runAt = T0.plusMinutes(6);
        repository.recordPoll(SCHEDULE_ID, firstActive.token().token(), 200L, runAt);
        repository.recordPoll(SCHEDULE_ID, secondActive.token().token(), 300L, runAt);

        QueueAdmissionResult result = repository.admit(runAt, admissionPolicy(10, 10, 1));

        assertThat(result).isEqualTo(QueueAdmissionResult.completed(1));
        assertThat(statusOf(inactive.token().token())).isEqualTo(QueueStatus.WAITING.name());
        assertThat(statusOf(firstActive.token().token())).isEqualTo(QueueStatus.ADMITTED.name());
        assertThat(statusOf(secondActive.token().token())).isEqualTo(QueueStatus.WAITING.name());
    }

    @Test
    void 회차별_FIFO와_회차_간_round_robin으로_입장시킨다() {
        List<QueueEnterResult> firstSchedule = enterMembers(1L, 100L, 3);
        List<QueueEnterResult> secondSchedule = enterMembers(2L, 200L, 3);

        QueueAdmissionResult result = repository.admit(T0, admissionPolicy(2, 3, 3));

        assertThat(result.admittedCount()).isEqualTo(3);
        assertThat(admittedMembers(1L)).containsExactlyInAnyOrder(
                firstSchedule.get(0).token().token(), firstSchedule.get(1).token().token());
        assertThat(admittedMembers(2L)).containsExactly(secondSchedule.get(0).token().token());
        assertThat(waitingMembers(1L)).containsExactly(firstSchedule.get(2).token().token());
        assertThat(waitingMembers(2L)).containsExactly(
                secondSchedule.get(1).token().token(), secondSchedule.get(2).token().token());
    }

    @Test
    void 실행_사이에도_회차_cursor를_유지해_다음_회차부터_입장시킨다() {
        QueueEnterResult firstSchedule = repository.enterOrResume(1L, 100L, T0);
        QueueEnterResult secondSchedule = repository.enterOrResume(2L, 200L, T0);
        QueueAdmissionPolicy onePerRun = admissionPolicy(10, 10, 1);

        repository.admit(T0, onePerRun);
        repository.admit(T0, onePerRun);

        assertThat(statusOf(1L, firstSchedule.token().token())).isEqualTo(QueueStatus.ADMITTED.name());
        assertThat(statusOf(2L, secondSchedule.token().token())).isEqualTo(QueueStatus.ADMITTED.name());
    }

    @Test
    void bounded_scan에서_제외한_inactive_후보는_다음_실행의_active_후보를_막지_않는다() {
        QueueEnterResult inactive = repository.enterOrResume(SCHEDULE_ID, 100L, T0);
        QueueEnterResult active = repository.enterOrResume(SCHEDULE_ID, 200L, T0);
        OffsetDateTime runAt = T0.plusMinutes(6);
        repository.recordPoll(SCHEDULE_ID, active.token().token(), 200L, runAt);
        QueueAdmissionPolicy oneCandidatePerRun = admissionPolicy(10, 10, 1, 1);

        QueueAdmissionResult firstRun = repository.admit(runAt, oneCandidatePerRun);
        QueueAdmissionResult secondRun = repository.admit(runAt, oneCandidatePerRun);

        assertThat(firstRun.admittedCount()).isZero();
        assertThat(secondRun.admittedCount()).isEqualTo(1);
        assertThat(statusOf(inactive.token().token())).isEqualTo(QueueStatus.WAITING.name());
        assertThat(statusOf(active.token().token())).isEqualTo(QueueStatus.ADMITTED.name());
    }

    @Test
    void active_후보가_없는_회차는_스캔에서_빠지고_polling하면_다시_등록된다() {
        QueueEnterResult entered = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);
        OffsetDateTime runAt = T0.plusMinutes(6);

        QueueAdmissionResult inactiveRun = repository.admit(runAt, admissionPolicy(10, 10, 1));

        assertThat(inactiveRun.admittedCount()).isZero();
        assertThat(admissionSchedules()).doesNotContain(String.valueOf(SCHEDULE_ID));

        repository.recordPoll(SCHEDULE_ID, entered.token().token(), MEMBER_ID, runAt);
        assertThat(admissionSchedules()).contains(String.valueOf(SCHEDULE_ID));

        assertThat(repository.admit(runAt, admissionPolicy(10, 10, 1)).admittedCount())
                .isEqualTo(1);
    }

    @Test
    void 회차별과_global_capacity를_동시에_넘지_않는다() {
        enterMembers(1L, 100L, 4);
        enterMembers(2L, 200L, 4);

        repository.admit(T0, admissionPolicy(2, 3, 100));

        assertThat(admittedSize(1L)).isLessThanOrEqualTo(2);
        assertThat(admittedSize(2L)).isLessThanOrEqualTo(2);
        assertThat(globalAdmittedSize()).isEqualTo(3);
    }

    @Test
    void 동시에_Admission을_실행해도_global_capacity를_넘지_않는다() throws Exception {
        enterMembers(1L, 100L, 10);
        enterMembers(2L, 200L, 10);
        QueueAdmissionPolicy limited = admissionPolicy(4, 5, 5);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<QueueAdmissionResult> first = executor.submit(() -> {
                start.await();
                return repository.admit(T0, limited);
            });
            Future<QueueAdmissionResult> second = executor.submit(() -> {
                start.await();
                return repository.admit(T0, limited);
            });
            start.countDown();
            first.get();
            second.get();
        }

        assertThat(globalAdmittedSize()).isEqualTo(5);
        assertThat(admittedSize(1L)).isLessThanOrEqualTo(4);
        assertThat(admittedSize(2L)).isLessThanOrEqualTo(4);
    }

    @Test
    void execution_lease를_우회해_Function이_동시에_호출되어도_capacity를_넘지_않는다() throws Exception {
        enterMembers(1L, 100L, 10);
        enterMembers(2L, 200L, 10);
        QueueAdmissionPolicy limited = admissionPolicy(4, 5, 5);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Map<String, String>> first = executor.submit(() -> {
                start.await();
                return callAdmissionFunction(limited);
            });
            Future<Map<String, String>> second = executor.submit(() -> {
                start.await();
                return callAdmissionFunction(limited);
            });
            start.countDown();
            first.get();
            second.get();
        }

        assertThat(globalAdmittedSize()).isEqualTo(5);
        assertThat(admittedSize(1L)).isLessThanOrEqualTo(4);
        assertThat(admittedSize(2L)).isLessThanOrEqualTo(4);
    }

    @Test
    void 실행_lease를_다른_scheduler가_보유하면_Admission을_건너뛴다() {
        repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);
        redisTemplate.opsForValue().set("queue:admission:execution-lease", "other", Duration.ofSeconds(1));

        QueueAdmissionResult result = repository.admit(T0, ADMISSION_POLICY);

        assertThat(result).isEqualTo(QueueAdmissionResult.leaseNotAcquired());
        assertThat(globalAdmittedSize()).isZero();
    }

    @Test
    void 최초_lease가_만료되면_capacity를_반환하고_다음_WAITING을_입장시킨다() {
        QueueEnterResult first = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);
        QueueEnterResult second = repository.enterOrResume(SCHEDULE_ID, OTHER_MEMBER_ID, T0);
        QueueAdmissionPolicy oneSeat = admissionPolicy(1, 1, 1);
        repository.admit(T0, oneSeat);

        QueueAdmissionResult result = repository.admit(T0.plusMinutes(5), oneSeat);

        assertThat(result.admittedCount()).isEqualTo(1);
        assertThat(statusOf(first.token().token())).isEqualTo("EXPIRED");
        assertThat(statusOf(second.token().token())).isEqualTo(QueueStatus.ADMITTED.name());
        assertThat(globalAdmittedSize()).isEqualTo(1);
        assertThat(repository.recordPoll(
                SCHEDULE_ID, first.token().token(), MEMBER_ID, T0.plusMinutes(5)).outcome())
                .isEqualTo(QueuePollOutcome.EXPIRED);
    }

    @Test
    void Admission은_최초_lease와_hard_cap을_각각_저장한다() {
        QueueEnterResult entered = repository.enterOrResume(SCHEDULE_ID, MEMBER_ID, T0);

        repository.admit(T0, ADMISSION_POLICY);

        assertThat(hashField(entered.token().token(), "admittedAt"))
                .isEqualTo(String.valueOf(T0.toInstant().toEpochMilli()));
        assertThat(hashField(entered.token().token(), "admittedUntil"))
                .isEqualTo(String.valueOf(T0.plusMinutes(5).toInstant().toEpochMilli()));
        assertThat(hashField(entered.token().token(), "admissionHardExpiresAt"))
                .isEqualTo(String.valueOf(T0.plusMinutes(30).toInstant().toEpochMilli()));
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

    private Set<String> waitingMembers(long scheduleId) {
        return redisTemplate.opsForZSet().range(
                "queue:{%d}:waiting".formatted(scheduleId), 0, -1);
    }

    private Set<String> admittedMembers(long scheduleId) {
        return redisTemplate.opsForZSet().range(
                "queue:{%d}:admitted".formatted(scheduleId), 0, -1);
    }

    private Set<String> admissionSchedules() {
        return redisTemplate.opsForZSet().range("queue:admission:schedules", 0, -1);
    }

    private long admittedSize(long scheduleId) {
        Long size = redisTemplate.opsForZSet().zCard("queue:{%d}:admitted".formatted(scheduleId));
        return size == null ? 0 : size;
    }

    private long globalAdmittedSize() {
        Long size = redisTemplate.opsForZSet().zCard("queue:admitted");
        return size == null ? 0 : size;
    }

    private String statusOf(String token) {
        return hashField(token, "status");
    }

    private String statusOf(long scheduleId, String token) {
        Object value = redisTemplate.opsForHash().get(
                "queue:{%d}:token:%s".formatted(scheduleId, token), "status");
        return value == null ? null : value.toString();
    }

    private List<QueueEnterResult> enterMembers(long scheduleId, long firstMemberId, int count) {
        List<QueueEnterResult> results = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            results.add(repository.enterOrResume(scheduleId, firstMemberId + index, T0));
        }
        return results;
    }

    private static QueueAdmissionPolicy admissionPolicy(
            int perScheduleCapacity, int globalCapacity, int maxAdmissionsPerRun) {
        return admissionPolicy(perScheduleCapacity, globalCapacity, maxAdmissionsPerRun, 1000);
    }

    private static QueueAdmissionPolicy admissionPolicy(
            int perScheduleCapacity, int globalCapacity, int maxAdmissionsPerRun,
            int candidateScanLimit) {
        return new QueueAdmissionPolicy(
                perScheduleCapacity,
                globalCapacity,
                maxAdmissionsPerRun,
                candidateScanLimit,
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                Duration.ofMinutes(30),
                Duration.ofSeconds(1));
    }

    private Map<String, String> callAdmissionFunction(QueueAdmissionPolicy admissionPolicy) {
        return functions.call(
                QueueFunctions.ADMIT,
                List.of(
                        QueueRedisKeys.admissionSchedules(),
                        QueueRedisKeys.admissionCursor(),
                        QueueRedisKeys.admitted()),
                String.valueOf(T0.toInstant().toEpochMilli()),
                QueueRedisKeys.root(),
                String.valueOf(admissionPolicy.waitingActivityWindow().toMillis()),
                String.valueOf(admissionPolicy.initialLease().toMillis()),
                String.valueOf(admissionPolicy.hardCap().toMillis()),
                String.valueOf(admissionPolicy.perScheduleCapacity()),
                String.valueOf(admissionPolicy.globalCapacity()),
                String.valueOf(admissionPolicy.maxAdmissionsPerRun()),
                String.valueOf(admissionPolicy.candidateScanLimit()),
                "200",
                "1000");
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
}
