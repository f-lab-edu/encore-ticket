package com.encore.ticket.core.booking.queue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.encore.ticket.core.booking.dto.QueueStatus;
import com.encore.ticket.core.booking.dto.QueueStatusResponse;
import com.encore.ticket.core.booking.dto.QueueTokenResponse;
import com.encore.ticket.core.booking.exception.QueueTokenExpiredException;
import com.encore.ticket.core.booking.exception.QueueTokenNotOwnedException;
import com.encore.ticket.core.booking.queue.domain.QueueToken;
import com.encore.ticket.core.booking.queue.port.QueueEnterResult;
import com.encore.ticket.core.booking.queue.port.QueuePollOutcome;
import com.encore.ticket.core.booking.queue.port.QueuePollResult;
import com.encore.ticket.core.booking.queue.port.QueueRepository;
import com.encore.ticket.core.exception.NotFoundException;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-04T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);
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
    void 새_토큰을_발급하면_재접속이_아니다() {
        given(queueRepository.enterOrResume(SCHEDULE_ID, MEMBER_ID, NOW))
                .willReturn(new QueueEnterResult(waitingToken(153, 2), true));

        QueueTokenResponse response = service.enter(SCHEDULE_ID, MEMBER_ID);

        assertThat(response.resumed()).isFalse();
        assertThat(response.queueToken()).isEqualTo(QUEUE_TOKEN);
        assertThat(response.position()).isEqualTo(153);
        assertThat(response.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.lapsesRemaining()).isEqualTo(2);
    }

    @Test
    void 기존_토큰을_유지하면_재접속이다() {
        given(queueRepository.enterOrResume(SCHEDULE_ID, MEMBER_ID, NOW))
                .willReturn(new QueueEnterResult(waitingToken(153, 1), false));

        QueueTokenResponse response = service.enter(SCHEDULE_ID, MEMBER_ID);

        assertThat(response.resumed()).isTrue();
        assertThat(response.queueToken()).isEqualTo(QUEUE_TOKEN);
        assertThat(response.position()).isEqualTo(153);
        assertThat(response.lapsesRemaining()).isEqualTo(1);
    }

    @Test
    void 진입_응답의_예상_대기_시간은_순번에_비례한다() {
        given(queueRepository.enterOrResume(SCHEDULE_ID, MEMBER_ID, NOW))
                .willReturn(new QueueEnterResult(waitingToken(153, 2), true));

        QueueTokenResponse response = service.enter(SCHEDULE_ID, MEMBER_ID);

        assertThat(response.estimatedWaitSeconds()).isEqualTo(306);
        assertThat(response.pollAfterSeconds()).isEqualTo(20);
    }

    @Test
    void 대기_중_토큰의_상태는_순번과_예상_대기_시간을_담는다() {
        givenPoll(QueuePollResult.updated(waitingToken(153, 2)));

        QueueStatusResponse response = service.status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID);

        assertThat(response.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.position()).isEqualTo(153);
        assertThat(response.estimatedWaitSeconds()).isEqualTo(306);
        assertThat(response.pollAfterSeconds()).isEqualTo(20);
        assertThat(response.admittedUntil()).isNull();
    }

    @Test
    void 대기_중_토큰에_지난_입장_시각이_남아_있어도_응답에_담지_않는다() {
        givenPoll(QueuePollResult.updated(QueueToken.builder()
                .token(QUEUE_TOKEN)
                .scheduleId(SCHEDULE_ID)
                .memberId(MEMBER_ID)
                .position(153)
                .sequence(153)
                .status(QueueStatus.WAITING)
                .lastPolledAt(NOW)
                .lapsesRemaining(2)
                .admittedUntil(OffsetDateTime.parse("2026-08-04T09:59:00Z"))
                .build()));

        QueueStatusResponse response = service.status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID);

        assertThat(response.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.admittedUntil()).isNull();
    }

    @Test
    void 입장_허용_토큰의_상태는_순번이_0이고_대기_정보가_없다() {
        OffsetDateTime admittedUntil = OffsetDateTime.parse("2026-08-04T10:03:00Z");
        givenPoll(QueuePollResult.updated(admittedToken(admittedUntil)));

        QueueStatusResponse response = service.status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID);

        assertThat(response.status()).isEqualTo(QueueStatus.ADMITTED);
        assertThat(response.position()).isZero();
        assertThat(response.admittedUntil()).isEqualTo(admittedUntil);
        assertThat(response.estimatedWaitSeconds()).isNull();
        assertThat(response.pollAfterSeconds()).isNull();
    }

    @Test
    void 입장_허용_시각에_도달하면_상태_조회가_실패한다() {
        givenPoll(QueuePollResult.updated(admittedToken(NOW)));

        assertThatThrownBy(() -> service.status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID))
                .isInstanceOf(QueueTokenExpiredException.class);
    }

    @Test
    void 저장소가_소유자_불일치를_알리면_상태_조회가_실패한다() {
        givenPoll(QueuePollResult.of(QueuePollOutcome.NOT_OWNED));

        assertThatThrownBy(() -> service.status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID))
                .isInstanceOf(QueueTokenNotOwnedException.class);
    }

    @Test
    void 저장소가_만료를_알리면_상태_조회가_실패한다() {
        givenPoll(QueuePollResult.of(QueuePollOutcome.EXPIRED));

        assertThatThrownBy(() -> service.status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID))
                .isInstanceOf(QueueTokenExpiredException.class);
    }

    @Test
    void 저장소에_토큰이_없으면_상태_조회가_실패한다() {
        givenPoll(QueuePollResult.of(QueuePollOutcome.NOT_FOUND));

        assertThatThrownBy(() -> service.status(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID))
                .isInstanceOf(NotFoundException.class);
    }

    private void givenPoll(QueuePollResult result) {
        given(queueRepository.recordPoll(SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID, NOW))
                .willReturn(result);
    }

    private QueueToken waitingToken(int position, int lapsesRemaining) {
        return QueueToken.builder()
                .token(QUEUE_TOKEN)
                .scheduleId(SCHEDULE_ID)
                .memberId(MEMBER_ID)
                .position(position)
                .sequence(position)
                .status(QueueStatus.WAITING)
                .lastPolledAt(NOW)
                .lapsesRemaining(lapsesRemaining)
                .build();
    }

    private QueueToken admittedToken(OffsetDateTime admittedUntil) {
        return QueueToken.builder()
                .token(QUEUE_TOKEN)
                .scheduleId(SCHEDULE_ID)
                .memberId(MEMBER_ID)
                .position(0)
                .sequence(153)
                .status(QueueStatus.ADMITTED)
                .lastPolledAt(NOW)
                .lapsesRemaining(2)
                .admittedUntil(admittedUntil)
                .build();
    }
}
