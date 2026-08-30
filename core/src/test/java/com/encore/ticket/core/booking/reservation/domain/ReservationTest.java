package com.encore.ticket.core.booking.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.encore.ticket.core.booking.dto.ReservationStatus;

class ReservationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-31T10:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime NOW = OffsetDateTime.now(CLOCK);

    @Test
    void 결제_대기_중이고_마감_시각이_지났으면_만료한다() {
        Reservation reservation = reservation(ReservationStatus.PENDING_PAYMENT, NOW.minusSeconds(1));

        Reservation expired = reservation.expire(NOW);

        assertThat(expired.status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(expired.seatIds()).containsExactly(101L);
    }

    @Test
    void 아직_마감되지_않았거나_결제_대기_상태가_아니면_만료하지_않는다() {
        Reservation future = reservation(ReservationStatus.PENDING_PAYMENT, NOW.plusSeconds(1));
        Reservation confirmed = reservation(ReservationStatus.CONFIRMED, NOW.minusSeconds(1));

        assertThatThrownBy(() -> future.expire(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> confirmed.expire(NOW)).isInstanceOf(IllegalStateException.class);
    }

    private Reservation reservation(ReservationStatus status, OffsetDateTime expiresAt) {
        return Reservation.builder()
                .id(1L)
                .version(0L)
                .holdId("hold-1")
                .memberId(10L)
                .scheduleId(20L)
                .seatIds(List.of(101L))
                .amount(100_000L)
                .status(status)
                .reservedAt(NOW.minusMinutes(10))
                .performanceStartsAt(NOW.plusDays(1))
                .originalExpiresAt(expiresAt)
                .expiresAt(expiresAt)
                .paymentAttemptNo(1)
                .build();
    }
}
