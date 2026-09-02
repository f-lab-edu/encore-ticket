package com.encore.ticket.core.booking.reservation.application;

import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.booking.exception.CancellationClosedException;
import com.encore.ticket.core.booking.exception.PaymentInProgressException;
import com.encore.ticket.core.booking.exception.ReservationConcurrentModificationException;
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
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC);
    private static final long RESERVATION_ID = 1L;
    private static final long MEMBER_ID = 100L;
    private static final long OTHER_MEMBER_ID = 200L;

    @Mock
    ReservationRepository reservationRepository;

    ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(reservationRepository, CLOCK);
    }

    @Test
    void 예매를_취소하면_CANCELLED_가_되고_취소_시각이_기록된다() {
        Reservation reservation = Reservation.builder()
                .id(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .performanceStartsAt(OffsetDateTime.parse("2026-08-04T10:30:00Z"))
                .status(ReservationStatus.PENDING_PAYMENT)
                .build();
        given(reservationRepository.getById(RESERVATION_ID)).willReturn(reservation);
        given(reservationRepository.saveCancelled(any())).willAnswer(call -> call.getArgument(0));

        CancelResult result = service.cancel(RESERVATION_ID, MEMBER_ID);

        assertThat(result.alreadyCancelled()).isFalse();
        assertThat(result.response().status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(result.response().cancelledAt()).isEqualTo(OffsetDateTime.parse("2026-08-04T19:00:00+09:00"));

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).saveCancelled(captor.capture());
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

        verify(reservationRepository, never()).saveCancelled(any());
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

        verify(reservationRepository, never()).saveCancelled(any());
    }

    @Test
    void 공연이_시작된_예매를_취소하면_실패한다() {
        Reservation reservation = Reservation.builder()
                .id(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .performanceStartsAt(OffsetDateTime.parse("2026-08-04T10:00:00Z"))
                .status(ReservationStatus.PENDING_PAYMENT)
                .build();
        given(reservationRepository.getById(RESERVATION_ID)).willReturn(reservation);

        assertThatThrownBy(() -> service.cancel(RESERVATION_ID, MEMBER_ID))
                .isInstanceOf(CancellationClosedException.class);

        verify(reservationRepository, never()).saveCancelled(any());
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

        verify(reservationRepository, never()).saveCancelled(any());
    }

    @Test
    void 결제_완료_예매는_환불_연동_전까지_취소하지_않는다() {
        Reservation reservation = cancellable().toBuilder()
                .status(ReservationStatus.CONFIRMED)
                .build();
        given(reservationRepository.getById(RESERVATION_ID)).willReturn(reservation);

        assertThatThrownBy(() -> service.cancel(RESERVATION_ID, MEMBER_ID))
                .isInstanceOf(CancellationClosedException.class);

        verify(reservationRepository, never()).saveCancelled(any());
    }

    @Test
    void 만료_시각이_지났어도_EXPIRED_전환_전의_PENDING_PAYMENT는_취소한다() {
        Reservation expiredByTime = cancellable().toBuilder()
                .expiresAt(OffsetDateTime.parse("2026-08-04T09:59:59Z"))
                .build();
        given(reservationRepository.getById(RESERVATION_ID)).willReturn(expiredByTime);
        given(reservationRepository.saveCancelled(any())).willAnswer(call -> call.getArgument(0));

        CancelResult result = service.cancel(RESERVATION_ID, MEMBER_ID);

        assertThat(result.response().status()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void 충돌_후_이미_취소됐으면_재저장하지_않고_재취소로_응답한다() {
        Reservation pending = cancellable().toBuilder().version(1L).build();
        Reservation cancelled = pending.cancel(CLOCK).toBuilder().version(2L).build();
        given(reservationRepository.getById(RESERVATION_ID)).willReturn(pending, cancelled);
        given(reservationRepository.saveCancelled(any()))
                .willThrow(new ReservationConcurrentModificationException(new RuntimeException()));

        CancelResult result = service.cancel(RESERVATION_ID, MEMBER_ID);

        assertThat(result.alreadyCancelled()).isTrue();
        verify(reservationRepository).saveCancelled(any());
    }

    @Test
    void 충돌_후에도_취소_가능하면_최신_버전으로_한번_재시도한다() {
        Reservation first = cancellable().toBuilder().version(1L).paymentAttemptNo(1).build();
        Reservation latest = first.toBuilder().version(2L).paymentAttemptNo(2).build();
        given(reservationRepository.getById(RESERVATION_ID)).willReturn(first, latest);
        given(reservationRepository.saveCancelled(any()))
                .willThrow(new ReservationConcurrentModificationException(new RuntimeException()))
                .willAnswer(call -> call.getArgument(0));

        CancelResult result = service.cancel(RESERVATION_ID, MEMBER_ID);

        assertThat(result.response().status()).isEqualTo(ReservationStatus.CANCELLED);
        verify(reservationRepository, times(2)).saveCancelled(any());
    }

    @Test
    void 재시도도_충돌하면_409로_변환할_동시_변경_예외를_유지한다() {
        Reservation first = cancellable().toBuilder().version(1L).build();
        Reservation latest = first.toBuilder().version(2L).paymentAttemptNo(2).build();
        given(reservationRepository.getById(RESERVATION_ID)).willReturn(first, latest);
        given(reservationRepository.saveCancelled(any()))
                .willThrow(new ReservationConcurrentModificationException(new RuntimeException()));

        assertThatThrownBy(() -> service.cancel(RESERVATION_ID, MEMBER_ID))
                .isInstanceOf(ReservationConcurrentModificationException.class);

        verify(reservationRepository, times(2)).saveCancelled(any());
    }

    private Reservation cancellable() {
        return Reservation.builder()
                .id(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .performanceStartsAt(OffsetDateTime.parse("2026-08-04T10:30:00Z"))
                .expiresAt(OffsetDateTime.parse("2026-08-04T10:10:00Z"))
                .status(ReservationStatus.PENDING_PAYMENT)
                .build();
    }
}
