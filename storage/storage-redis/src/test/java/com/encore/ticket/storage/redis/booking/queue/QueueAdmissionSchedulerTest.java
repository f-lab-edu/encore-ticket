package com.encore.ticket.storage.redis.booking.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.encore.ticket.core.booking.queue.domain.QueueAdmissionPolicy;
import com.encore.ticket.core.booking.queue.port.QueueAdmissionResult;
import com.encore.ticket.core.booking.queue.port.QueueRepository;

@ExtendWith(MockitoExtension.class)
class QueueAdmissionSchedulerTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-22T00:00:00Z");
    private static final QueueAdmissionPolicy POLICY = new QueueAdmissionPolicy(
            100, 500, 100, 1000, Duration.ofMinutes(5), Duration.ofMinutes(5),
            Duration.ofMinutes(30), Duration.ofMillis(900));

    @Mock
    QueueRepository queueRepository;

    @Test
    void 현재_시각과_설정된_정책으로_Admission을_실행한다() {
        QueueAdmissionResult expected = QueueAdmissionResult.completed(3);
        given(queueRepository.admit(NOW, POLICY)).willReturn(expected);
        QueueAdmissionScheduler scheduler = new QueueAdmissionScheduler(
                queueRepository, POLICY, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

        QueueAdmissionResult result = scheduler.admitAt(NOW);

        assertThat(result).isEqualTo(expected);
        verify(queueRepository).admit(NOW, POLICY);
    }
}
