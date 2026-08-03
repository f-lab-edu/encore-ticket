package com.encore.ticket.booking.internal.queue;


import com.encore.ticket.booking.api.dto.QueueStatus;
import com.encore.ticket.booking.api.dto.QueueStatusResponse;
import com.encore.ticket.booking.api.dto.QueueTokenResponse;
import com.encore.ticket.booking.api.exception.QueueTokenExpiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC);
    private static final long SCHEDULE_ID = 1L;
    private static final long MEMBER_ID = 100L;
    private static final String QUEUE_TOKEN = "q_existing";

    @Mock
    QueueRepository queueRepository;

    QueueService service;

    @BeforeEach
    void setUp() {
        service = new QueueService(queueRepository, CLOCK);
    }

    @Test
    void 처음_진입하면_토큰이_발급되고_WAITING_이다() {
        given(queueRepository.countWaiting(SCHEDULE_ID)).willReturn(153);

        QueueTokenResponse response = service.enter(SCHEDULE_ID, MEMBER_ID);

        assertThat(response.resumed()).isFalse();
        assertThat(response.queueToken()).startsWith("q_");
        assertThat(response.position()).isEqualTo(154);
        assertThat(response.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.lapsesRemaining()).isEqualTo(2);

        verify(queueRepository).save(any());
    }

    @Test
    void 마지막_폴링_5분_이내에_재진입하면_순번이_유지되고_유예를_쓰지_않는다() {
        QueueToken existing = new QueueToken(
                "q_existing",
                SCHEDULE_ID, MEMBER_ID,
                153,
                QueueStatus.WAITING,
                OffsetDateTime.parse("2026-08-04T09:55:00Z"),
                2, null
        );
        given(queueRepository.findActiveToken(SCHEDULE_ID, MEMBER_ID)).willReturn(Optional.of(existing));

        QueueTokenResponse response = service.enter(SCHEDULE_ID, MEMBER_ID);

        assertThat(response.resumed()).isTrue();
        assertThat(response.queueToken()).isEqualTo("q_existing");
        assertThat(response.position()).isEqualTo(153);
        assertThat(response.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.lapsesRemaining()).isEqualTo(2);

        verify(queueRepository).save(existing);
    }

    @Test
    void 마지막_폴링_5분_초과_후_재진입하면_유예를_한_번_쓴다() {
        QueueToken existing = new QueueToken(
                "q_existing",
                SCHEDULE_ID, MEMBER_ID,
                153,
                QueueStatus.WAITING,
                OffsetDateTime.parse("2026-08-04T09:54:59Z"),
                2, null
        );
        given(queueRepository.findActiveToken(SCHEDULE_ID, MEMBER_ID)).willReturn(Optional.of(existing));

        QueueTokenResponse response = service.enter(SCHEDULE_ID, MEMBER_ID);

        assertThat(response.resumed()).isTrue();
        assertThat(response.queueToken()).isEqualTo("q_existing");
        assertThat(response.position()).isEqualTo(153);
        assertThat(response.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.lapsesRemaining()).isEqualTo(1);

        verify(queueRepository).save(existing);
    }

    @Test
    void 유예를_다_쓰고_재진입하면_새_토큰이_발급되고_순번이_초기화된다() {
        QueueToken existing = new QueueToken(
                "q_existing",
                SCHEDULE_ID, MEMBER_ID,
                153,
                QueueStatus.WAITING,
                OffsetDateTime.parse("2026-08-04T09:54:59Z"),
                0, null
        );
        given(queueRepository.findActiveToken(SCHEDULE_ID, MEMBER_ID)).willReturn(Optional.of(existing));
        given(queueRepository.countWaiting(SCHEDULE_ID)).willReturn(256);

        QueueTokenResponse response = service.enter(SCHEDULE_ID, MEMBER_ID);

        assertThat(response.resumed()).isFalse();
        assertThat(response.queueToken()).isNotEqualTo("q_existing");
        assertThat(response.queueToken()).startsWith("q_");
        assertThat(response.position()).isEqualTo(257);
        assertThat(response.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.lapsesRemaining()).isEqualTo(2);

        verify(queueRepository).save(any());
    }

    @Test
    void 유예를_쓴_뒤_5분_이내에_다시_들어오면_유예를_또_쓰지_않는다() {
        QueueToken existing = new QueueToken(
                "q_existing",
                SCHEDULE_ID, MEMBER_ID,
                153,
                QueueStatus.WAITING,
                OffsetDateTime.parse("2026-08-04T09:54:59Z"),
                2, null
        );
        given(queueRepository.findActiveToken(SCHEDULE_ID, MEMBER_ID)).willReturn(Optional.of(existing));

        service.enter(SCHEDULE_ID, MEMBER_ID);
        QueueTokenResponse second = service.enter(SCHEDULE_ID, MEMBER_ID);

        assertThat(second.lapsesRemaining()).isEqualTo(1);

        verify(queueRepository, times(2)).save(existing);
    }

    private QueueToken waitingToken(OffsetDateTime lastPolledAt, int lapsesRemaining) {
        return new QueueToken(
                QUEUE_TOKEN, SCHEDULE_ID, MEMBER_ID, 153,
                QueueStatus.WAITING, lastPolledAt, lapsesRemaining, null);
    }

    private QueueToken admittedToken(OffsetDateTime admittedUntil) {
        return new QueueToken(
                QUEUE_TOKEN, SCHEDULE_ID, MEMBER_ID, 0,
                QueueStatus.ADMITTED, OffsetDateTime.parse("2026-08-04T09:58:00Z"), 2, admittedUntil);
    }

    private QueueService serviceAt(String instant) {
        return new QueueService(queueRepository, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test
    void 대기_중_토큰의_상태는_순번과_예상_대기_시간을_담는다() {
        given(queueRepository.findByToken(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID))
                .willReturn(waitingToken(OffsetDateTime.parse("2026-08-04T09:58:00Z"), 2));

        QueueStatusResponse response = service.status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID);

        assertThat(response.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.position()).isEqualTo(153);
        assertThat(response.estimatedWaitSeconds()).isEqualTo(306);
        assertThat(response.pollAfterSeconds()).isEqualTo(20);
        assertThat(response.admittedUntil()).isNull();
    }

    @Test
    void 대기_중_토큰에_지난_입장_시각이_남아_있어도_응답에_담지_않는다() {
        given(queueRepository.findByToken(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID)).willReturn(new QueueToken(
                QUEUE_TOKEN, SCHEDULE_ID, MEMBER_ID, 153, QueueStatus.WAITING,
                OffsetDateTime.parse("2026-08-04T09:58:00Z"), 2,
                OffsetDateTime.parse("2026-08-04T09:59:00Z")));

        QueueStatusResponse response = service.status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID);

        assertThat(response.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.admittedUntil()).isNull();
    }

    @Test
    void 입장_허용_토큰의_상태는_순번이_0이고_대기_정보가_없다() {
        OffsetDateTime admittedUntil = OffsetDateTime.parse("2026-08-04T10:03:00Z");
        given(queueRepository.findByToken(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID))
                .willReturn(admittedToken(admittedUntil));

        QueueStatusResponse response = service.status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID);

        assertThat(response.status()).isEqualTo(QueueStatus.ADMITTED);
        assertThat(response.position()).isZero();
        assertThat(response.admittedUntil()).isEqualTo(admittedUntil);
        assertThat(response.estimatedWaitSeconds()).isNull();
        assertThat(response.pollAfterSeconds()).isNull();

        verify(queueRepository, never()).save(any());
    }

    @Test
    void 입장_허용_시각에_도달하면_상태_조회가_실패한다() {
        given(queueRepository.findByToken(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID))
                .willReturn(admittedToken(OffsetDateTime.parse("2026-08-04T10:00:00Z")));

        assertThatThrownBy(() -> service.status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID))
                .isInstanceOf(QueueTokenExpiredException.class);

        verify(queueRepository, never()).save(any());
    }

    @Test
    void 유예_시간_안에_상태를_조회하면_유예를_쓰지_않는다() {
        QueueToken token = waitingToken(OffsetDateTime.parse("2026-08-04T09:55:00Z"), 2);
        given(queueRepository.findByToken(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID)).willReturn(token);

        service.status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID);

        assertThat(token.lapsesRemaining()).isEqualTo(2);

        verify(queueRepository).save(token);
    }

    @Test
    void 유예_시간을_넘겨_상태를_조회하면_유예를_한_번_쓴다() {
        QueueToken token = waitingToken(OffsetDateTime.parse("2026-08-04T09:54:59Z"), 2);
        given(queueRepository.findByToken(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID)).willReturn(token);

        service.status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID);

        assertThat(token.lapsesRemaining()).isEqualTo(1);

        verify(queueRepository).save(token);
    }

    @Test
    void 유예를_다_쓰고_유예_시간을_넘겨_조회하면_실패한다() {
        given(queueRepository.findByToken(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID))
                .willReturn(waitingToken(OffsetDateTime.parse("2026-08-04T09:54:59Z"), 0));

        assertThatThrownBy(() -> service.status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID))
                .isInstanceOf(QueueTokenExpiredException.class);

        verify(queueRepository, never()).save(any());
    }

    @Test
    void 상태를_조회하면_마지막_폴링_시각이_갱신된다() {
        QueueToken token = waitingToken(OffsetDateTime.parse("2026-08-04T10:00:00Z"), 2);
        given(queueRepository.findByToken(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID)).willReturn(token);

        serviceAt("2026-08-04T10:04:00Z").status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID);
        serviceAt("2026-08-04T10:08:00Z").status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID);

        assertThat(token.lapsesRemaining()).isEqualTo(2);
    }
}
