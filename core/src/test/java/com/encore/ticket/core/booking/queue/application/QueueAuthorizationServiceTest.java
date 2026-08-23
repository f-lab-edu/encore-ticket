package com.encore.ticket.core.booking.queue.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.encore.ticket.core.booking.exception.QueueNotAdmittedException;
import com.encore.ticket.core.booking.exception.QueueTokenExpiredException;
import com.encore.ticket.core.booking.exception.QueueTokenNotOwnedException;
import com.encore.ticket.core.booking.queue.domain.QueueAuthorizationPolicy;
import com.encore.ticket.core.booking.queue.port.QueueAuthorizationOutcome;
import com.encore.ticket.core.booking.queue.port.QueueRepository;

@ExtendWith(MockitoExtension.class)
class QueueAuthorizationServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-23T10:00:00Z");
    private static final Duration RENEWAL_WINDOW = Duration.ofMinutes(5);
    private static final long SCHEDULE_ID = 1L;
    private static final long MEMBER_ID = 100L;
    private static final String QUEUE_TOKEN = "q_admitted";

    @Mock
    QueueRepository queueRepository;

    QueueAuthorizationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);
        service = new QueueAuthorizationService(
                queueRepository, new QueueAuthorizationPolicy(RENEWAL_WINDOW), clock);
    }

    @Test
    void 유효한_토큰은_좌석_접근을_허용한다() {
        givenOutcome(QueueAuthorizationOutcome.AUTHORIZED);

        assertThatCode(() -> service.authorize(SCHEDULE_ID, MEMBER_ID, QUEUE_TOKEN))
                .doesNotThrowAnyException();
    }

    @Test
    void 없거나_입장되지_않은_토큰은_입장을_거절한다() {
        givenOutcome(QueueAuthorizationOutcome.NOT_ADMITTED);

        assertThatThrownBy(() -> service.authorize(SCHEDULE_ID, MEMBER_ID, QUEUE_TOKEN))
                .isInstanceOf(QueueNotAdmittedException.class);
    }

    @Test
    void 다른_회원의_토큰은_소유권_오류로_거절한다() {
        givenOutcome(QueueAuthorizationOutcome.NOT_OWNED);

        assertThatThrownBy(() -> service.authorize(SCHEDULE_ID, MEMBER_ID, QUEUE_TOKEN))
                .isInstanceOf(QueueTokenNotOwnedException.class);
    }

    @Test
    void 만료된_토큰은_만료_오류로_거절한다() {
        givenOutcome(QueueAuthorizationOutcome.EXPIRED);

        assertThatThrownBy(() -> service.authorize(SCHEDULE_ID, MEMBER_ID, QUEUE_TOKEN))
                .isInstanceOf(QueueTokenExpiredException.class);
    }

    private void givenOutcome(QueueAuthorizationOutcome outcome) {
        given(queueRepository.authorizeAndRenew(
                SCHEDULE_ID, QUEUE_TOKEN, MEMBER_ID, NOW, RENEWAL_WINDOW))
                .willReturn(outcome);
    }
}
