package com.encore.ticket.storage.db.booking.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.encore.ticket.core.booking.reservation.port.ReservationRepository;

@ExtendWith(MockitoExtension.class)
class ReservationExpirySchedulerTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-31T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-31T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    ReservationRepository reservationRepository;

    @Test
    void 현재_시각과_batch_크기로_만료_처리를_실행한다() {
        given(reservationRepository.expireBatch(NOW, 10)).willReturn(3);
        ReservationExpiryScheduler scheduler = new ReservationExpiryScheduler(reservationRepository, CLOCK, 10);

        int expiredCount = scheduler.expireAt(NOW);

        assertThat(expiredCount).isEqualTo(3);
        verify(reservationRepository).expireBatch(NOW, 10);
    }

    @Test
    void batch가_실패해도_scheduler_실행_경계에서_예외를_전파하지_않는다() {
        given(reservationRepository.expireBatch(NOW, 10)).willThrow(new IllegalStateException("failure"));
        ReservationExpiryScheduler scheduler = new ReservationExpiryScheduler(reservationRepository, CLOCK, 10);

        assertThatCode(scheduler::expire).doesNotThrowAnyException();
        verify(reservationRepository).expireBatch(NOW, 10);
    }
}
