package com.encore.ticket.core.booking.reservation.application;

import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.booking.exception.CancellationClosedException;
import com.encore.ticket.core.booking.exception.PaymentInProgressException;
import com.encore.ticket.core.booking.exception.ReservationNotOwnedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;
import com.encore.ticket.core.booking.seat.port.SeatAssignmentRepository;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC);
    private static final long RESERVATION_ID = 1L;
    private static final long MEMBER_ID = 100L;
    private static final long OTHER_MEMBER_ID = 200L;

    @Mock
    ReservationRepository reservationRepository;

    @Mock
    SeatAssignmentRepository seatAssignmentRepository;

    ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(reservationRepository, seatAssignmentRepository, CLOCK);
    }

    @Test
    void 예매를_취소하면_CANCELLED_가_되고_취소_시각이_기록된다() {
        Reservation reservation = Reservation.builder()
                .id(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .performanceStartsAt(OffsetDateTime.parse("2026-08-04T10:30:00Z"))
                .status(ReservationStatus.CONFIRMED)
                .build();
        given(reservationRepository.getById(RESERVATION_ID)).willReturn(reservation);
        CancelResult result = service.cancel(RESERVATION_ID, MEMBER_ID);

        assertThat(result.alreadyCancelled()).isFalse();
        assertThat(result.response().status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(result.response().cancelledAt()).isEqualTo(OffsetDateTime.parse("2026-08-04T10:00:00Z"));

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(RESERVATION_ID);
        assertThat(captor.getValue().status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(captor.getValue().cancelledAt()).isEqualTo(OffsetDateTime.parse("2026-08-04T10:00:00Z"));
    }

    @Test
    void 다른_사용자의_예매를_취소하면_실패한다() {
        Reservation reservation = Reservation.builder()
                .id(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .performanceStartsAt(OffsetDateTime.parse("2026-08-04T10:30:00Z"))
                .status(ReservationStatus.CANCELLED)
                .cancelledAt(OffsetDateTime.parse("2026-08-04T10:00:00Z"))
                .build();
        given(reservationRepository.getById(RESERVATION_ID)).willReturn(reservation);

        assertThatThrownBy(() -> service.cancel(RESERVATION_ID, OTHER_MEMBER_ID))
                .isInstanceOf(ReservationNotOwnedException.class);

        verify(reservationRepository, never()).save(reservation);
    }

    @Test
    void 이미_취소된_예매를_다시_취소해도_성공한다() {
        Reservation reservation = Reservation.builder()
                .id(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .performanceStartsAt(OffsetDateTime.parse("2026-08-04T10:30:00Z"))
                .status(ReservationStatus.CANCELLED)
                .cancelledAt(OffsetDateTime.parse("2026-08-04T10:00:00Z"))
                .build();
        given(reservationRepository.getById(RESERVATION_ID)).willReturn(reservation);
        CancelResult result = service.cancel(RESERVATION_ID, MEMBER_ID);

        assertThat(result.alreadyCancelled()).isTrue();
        assertThat(result.response()).isNull();

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 공연이_시작된_예매를_취소하면_실패한다() {
        Reservation reservation = Reservation.builder()
                .id(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .performanceStartsAt(OffsetDateTime.parse("2026-08-04T10:00:00Z"))
                .status(ReservationStatus.CONFIRMED)
                .build();
        given(reservationRepository.getById(RESERVATION_ID)).willReturn(reservation);

        assertThatThrownBy(() -> service.cancel(RESERVATION_ID, MEMBER_ID))
                .isInstanceOf(CancellationClosedException.class);

        verify(reservationRepository, never()).save(reservation);
    }

    @Test
    void 결제_진행_중인_예매를_취소하면_실패한다() {
        Reservation reservation = Reservation.builder()
                .id(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .performanceStartsAt(OffsetDateTime.parse("2026-08-04T10:30:00Z"))
                .status(ReservationStatus.PENDING_PAYMENT)
                .paymentStartsAt(OffsetDateTime.parse("2026-08-04T09:00:00Z"))
                .build();
        given(reservationRepository.getById(RESERVATION_ID)).willReturn(reservation);

        assertThatThrownBy(() -> service.cancel(RESERVATION_ID, MEMBER_ID))
                .isInstanceOf(PaymentInProgressException.class);

        verify(reservationRepository, never()).save(reservation);
    }
}
