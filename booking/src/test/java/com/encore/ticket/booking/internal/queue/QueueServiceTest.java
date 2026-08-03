package com.encore.ticket.booking.internal.queue;


import com.encore.ticket.booking.api.dto.QueueStatus;
import com.encore.ticket.booking.api.dto.QueueTokenResponse;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC);
    private static final long SCHEDULE_ID = 1L;
    private static final long MEMBER_ID = 100L;

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
                2
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
                2
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
                0
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
                2
        );
        given(queueRepository.findActiveToken(SCHEDULE_ID, MEMBER_ID)).willReturn(Optional.of(existing));

        service.enter(SCHEDULE_ID, MEMBER_ID);
        QueueTokenResponse second = service.enter(SCHEDULE_ID, MEMBER_ID);

        assertThat(second.lapsesRemaining()).isEqualTo(1);

        verify(queueRepository, times(2)).save(existing);
    }
}
