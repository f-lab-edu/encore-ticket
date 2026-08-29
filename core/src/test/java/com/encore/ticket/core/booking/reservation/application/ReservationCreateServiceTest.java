package com.encore.ticket.core.booking.reservation.application;

import com.encore.ticket.core.booking.PaymentAttemptState;
import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.booking.exception.HoldExpiredException;
import com.encore.ticket.core.booking.exception.HoldNotOwnedException;
import com.encore.ticket.core.booking.exception.ReservationCancelledException;
import com.encore.ticket.core.booking.exception.ReservationAlreadyExistsException;
import com.encore.ticket.core.booking.exception.SeatAlreadyHeldException;
import com.encore.ticket.core.catalog.port.ScheduleCatalogReader;
import com.encore.ticket.core.catalog.domain.ScheduleInfo;
import com.encore.ticket.core.catalog.port.SeatCatalogReader;
import com.encore.ticket.core.catalog.domain.SeatInfo;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import com.encore.ticket.core.booking.reservation.domain.HeldSeats;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.HoldReader;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;

@ExtendWith(MockitoExtension.class)
class ReservationCreateServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC);

    private static final String HOLD_ID = "hold_7f32";
    private static final long RESERVATION_ID = 501L;
    private static final long SCHEDULE_ID = 101L;
    private static final long MEMBER_ID = 100L;
    private static final long OTHER_MEMBER_ID = 200L;
    private static final List<Long> SEAT_IDS = List.of(1001L, 1002L);
    private static final long SEAT_PRICE = 165_000L;
    private static final long TOTAL_AMOUNT = 330_000L;
    private static final OffsetDateTime HOLD_EXPIRES_AT = OffsetDateTime.parse("2026-08-04T10:05:00Z");
    private static final OffsetDateTime PAYMENT_DEADLINE = OffsetDateTime.parse("2026-08-04T10:10:00Z");
    private static final OffsetDateTime EXTENDED_EXPIRES_AT = OffsetDateTime.parse("2026-08-04T10:12:00Z");
    private static final OffsetDateTime PERFORMANCE_STARTS_AT = OffsetDateTime.parse("2026-09-01T09:00:00Z");
    private static final String CONCERT_TITLE = "2026 아이유 콘서트";

    @Mock ReservationRepository reservationRepository;
    @Mock HoldReader holdReader;
    @Mock SeatCatalogReader seatCatalogReader;
    @Mock ScheduleCatalogReader scheduleCatalogReader;

    ReservationCreateService service;

    @BeforeEach
    void setUp() {
        service = new ReservationCreateService(
                reservationRepository, holdReader,
                seatCatalogReader, scheduleCatalogReader, CLOCK);
    }

    private HeldSeats hold(Long ownerId, OffsetDateTime expiresAt) {
        return new HeldSeats(HOLD_ID, SCHEDULE_ID, SEAT_IDS, ownerId, expiresAt);
    }

    private void givenCatalog(List<SeatInfo> seats) {
        given(seatCatalogReader.seatsByIds(SEAT_IDS)).willReturn(seats);
        given(scheduleCatalogReader.scheduleOf(SCHEDULE_ID)).willReturn(new ScheduleInfo(
                SCHEDULE_ID, PERFORMANCE_STARTS_AT, "KSPO DOME",
                1L, CONCERT_TITLE, "https://example.com/poster.jpg"));
    }

    private List<SeatInfo> vipSeats() {
        return List.of(
                new SeatInfo(1001L, SCHEDULE_ID, "A구역", "1열", "1번", "VIP", SEAT_PRICE),
                new SeatInfo(1002L, SCHEDULE_ID, "A구역", "1열", "2번", "VIP", SEAT_PRICE));
    }

    private Reservation existing(ReservationStatus status, int paymentAttemptNo, OffsetDateTime expiresAt) {
        return Reservation.builder()
                .id(RESERVATION_ID)
                .holdId(HOLD_ID)
                .memberId(MEMBER_ID)
                .scheduleId(SCHEDULE_ID)
                .seatIds(SEAT_IDS)
                .amount(TOTAL_AMOUNT)
                .status(status)
                .expiresAt(expiresAt)
                .originalExpiresAt(PAYMENT_DEADLINE)
                .performanceStartsAt(PERFORMANCE_STARTS_AT)
                .reservedAt(OffsetDateTime.parse("2026-08-04T09:58:00Z"))
                .paymentAttemptNo(paymentAttemptNo)
                .build();
    }

    @Test
    void 최초_요청이면_결제_창을_새로_열어_결제_대기_예매를_만든다() {
        given(holdReader.getByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        given(reservationRepository.findByHoldId(HOLD_ID)).willReturn(Optional.empty());
        givenCatalog(vipSeats());
        given(reservationRepository.saveIssued(any()))
                .willReturn(existing(ReservationStatus.PENDING_PAYMENT, 1, PAYMENT_DEADLINE));

        CreateResult result = service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE);

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).saveIssued(captor.capture());
        Reservation created = captor.getValue();

        assertThat(created.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(created.amount()).isEqualTo(TOTAL_AMOUNT);
        assertThat(created.scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(created.seatIds()).containsExactly(1001L, 1002L);
        assertThat(created.reservedAt()).isEqualTo(OffsetDateTime.parse("2026-08-04T10:00:00Z"));
        assertThat(created.paymentAttemptNo()).isEqualTo(1);

        assertThat(result.created()).isTrue();
        assertThat(result.response().reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(result.response().orderId()).isEqualTo("reservation-501-1");
        assertThat(result.response().amount()).isEqualTo(TOTAL_AMOUNT);
        assertThat(result.response().status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    @Test
    void 결제_창은_선점_만료가_아니라_예매_생성_시점부터_10분이다() {
        given(holdReader.getByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        given(reservationRepository.findByHoldId(HOLD_ID)).willReturn(Optional.empty());
        givenCatalog(vipSeats());
        given(reservationRepository.saveIssued(any()))
                .willReturn(existing(ReservationStatus.PENDING_PAYMENT, 1, PAYMENT_DEADLINE));

        service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE);

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).saveIssued(captor.capture());
        Reservation created = captor.getValue();

        assertThat(created.expiresAt()).isEqualTo(PAYMENT_DEADLINE);
        assertThat(created.originalExpiresAt()).isEqualTo(PAYMENT_DEADLINE);
        assertThat(HOLD_EXPIRES_AT).isBefore(PAYMENT_DEADLINE);
    }

    @Test
    void 주문명은_콘서트명과_등급과_나머지_매수를_담는다() {
        given(holdReader.getByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        given(reservationRepository.findByHoldId(HOLD_ID)).willReturn(Optional.empty());
        givenCatalog(vipSeats());
        given(reservationRepository.saveIssued(any()))
                .willReturn(existing(ReservationStatus.PENDING_PAYMENT, 1, HOLD_EXPIRES_AT));

        CreateResult result = service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE);

        assertThat(result.response().orderName()).isEqualTo("2026 아이유 콘서트 VIP석 외 1매");
    }

    @Test
    void 좌석이_한_장이면_주문명에_나머지_매수를_붙이지_않는다() {
        given(holdReader.getByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        given(reservationRepository.findByHoldId(HOLD_ID)).willReturn(Optional.empty());
        givenCatalog(List.of(new SeatInfo(1001L, SCHEDULE_ID, "A구역", "1열", "1번", "VIP", SEAT_PRICE)));
        given(reservationRepository.saveIssued(any()))
                .willReturn(existing(ReservationStatus.PENDING_PAYMENT, 1, HOLD_EXPIRES_AT));

        CreateResult result = service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE);

        assertThat(result.response().orderName()).isEqualTo("2026 아이유 콘서트 VIP석");
    }

    @Test
    void 다른_사용자의_선점으로_예매를_생성하면_실패한다() {
        given(holdReader.getByHoldId(HOLD_ID)).willReturn(hold(OTHER_MEMBER_ID, HOLD_EXPIRES_AT));

        assertThatThrownBy(() -> service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE))
                .isInstanceOf(HoldNotOwnedException.class);

        verify(reservationRepository, never()).saveIssued(any());
    }

    @Test
    void 선점_만료_시각에_도달한_뒤_예매를_생성하면_실패한다() {
        given(holdReader.getByHoldId(HOLD_ID))
                .willReturn(hold(MEMBER_ID, OffsetDateTime.parse("2026-08-04T10:00:00Z")));
        given(reservationRepository.findByHoldId(HOLD_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE))
                .isInstanceOf(HoldExpiredException.class);

        verify(reservationRepository, never()).saveIssued(any());
    }

    @Test
    void 결제_시도가_없던_예매로_재요청하면_기존_주문번호를_그대로_돌려준다() {
        given(reservationRepository.findByHoldId(HOLD_ID))
                .willReturn(Optional.of(existing(ReservationStatus.PENDING_PAYMENT, 1, EXTENDED_EXPIRES_AT)));
        givenCatalog(vipSeats());

        CreateResult result = service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE);

        assertThat(result.created()).isFalse();
        assertThat(result.response().orderId()).isEqualTo("reservation-501-1");
        assertThat(result.response().expiresAt()).isEqualTo(EXTENDED_EXPIRES_AT);
        assertThat(result.response().originalExpiresAt()).isEqualTo(PAYMENT_DEADLINE);

        verifyNoInteractions(holdReader);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 결제가_진행_중이면_새_주문번호를_발급하지_않는다() {
        given(reservationRepository.findByHoldId(HOLD_ID))
                .willReturn(Optional.of(existing(ReservationStatus.PENDING_PAYMENT, 1, HOLD_EXPIRES_AT)));
        givenCatalog(vipSeats());

        CreateResult result = service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.PENDING);

        assertThat(result.created()).isFalse();
        assertThat(result.response().orderId()).isEqualTo("reservation-501-1");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 직전_결제가_실패했으면_다음_시도번호로_주문번호를_발급한다() {
        Reservation reservation = existing(ReservationStatus.PENDING_PAYMENT, 1, HOLD_EXPIRES_AT);
        given(reservationRepository.findByHoldId(HOLD_ID)).willReturn(Optional.of(reservation));
        given(reservationRepository.prepareNextPaymentAttempt(HOLD_ID, MEMBER_ID))
                .willReturn(reservation.startNextPaymentAttempt());
        givenCatalog(vipSeats());

        CreateResult result = service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.FAILED);

        assertThat(result.created()).isFalse();
        assertThat(result.response().orderId()).isEqualTo("reservation-501-2");

        verify(reservationRepository).prepareNextPaymentAttempt(HOLD_ID, MEMBER_ID);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 확정된_예매로_재요청하면_확정_상태를_그대로_돌려준다() {
        given(reservationRepository.findByHoldId(HOLD_ID))
                .willReturn(Optional.of(existing(ReservationStatus.CONFIRMED, 1, OffsetDateTime.now(CLOCK).minusMinutes(1))));
        givenCatalog(vipSeats());

        CreateResult result = service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.COMPLETED);

        assertThat(result.created()).isFalse();
        assertThat(result.response().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(result.response().orderId()).isEqualTo("reservation-501-1");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 취소된_예매의_선점으로_재요청하면_실패한다() {
        given(reservationRepository.findByHoldId(HOLD_ID))
                .willReturn(Optional.of(existing(ReservationStatus.CANCELLED, 1, HOLD_EXPIRES_AT)));

        assertThatThrownBy(() -> service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE))
                .isInstanceOf(ReservationCancelledException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 만료된_예매의_선점으로_재요청하면_실패한다() {
        given(reservationRepository.findByHoldId(HOLD_ID))
                .willReturn(Optional.of(existing(ReservationStatus.EXPIRED, 1, HOLD_EXPIRES_AT)));

        assertThatThrownBy(() -> service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE))
                .isInstanceOf(HoldExpiredException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 예매_만료_시각에_도달했으면_재요청도_실패한다() {
        given(reservationRepository.findByHoldId(HOLD_ID)).willReturn(Optional.of(existing(
                ReservationStatus.PENDING_PAYMENT, 1, OffsetDateTime.parse("2026-08-04T10:00:00Z"))));

        assertThatThrownBy(() -> service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE))
                .isInstanceOf(HoldExpiredException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 기존_예매의_소유자가_다르면_Redis를_조회하지_않고_거절한다() {
        given(reservationRepository.findByHoldId(HOLD_ID))
                .willReturn(Optional.of(existing(ReservationStatus.PENDING_PAYMENT, 1, PAYMENT_DEADLINE)));

        assertThatThrownBy(() -> service.create(HOLD_ID, OTHER_MEMBER_ID, PaymentAttemptState.FAILED))
                .isInstanceOf(HoldNotOwnedException.class);

        verifyNoInteractions(holdReader, seatCatalogReader, scheduleCatalogReader);
        verify(reservationRepository, never()).prepareNextPaymentAttempt(any(), any());
    }

    @Test
    void 과거_실패_힌트가_있어도_저장소가_반환한_현재_주문번호를_사용한다() {
        Reservation current = existing(ReservationStatus.PENDING_PAYMENT, 2, PAYMENT_DEADLINE);
        given(reservationRepository.findByHoldId(HOLD_ID)).willReturn(Optional.of(current));
        given(reservationRepository.prepareNextPaymentAttempt(HOLD_ID, MEMBER_ID)).willReturn(current);
        givenCatalog(vipSeats());

        CreateResult result = service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.FAILED);

        assertThat(result.response().orderId()).isEqualTo("reservation-501-2");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 같은_선점의_동시_저장_충돌이면_다시_조회한_기존_예매를_반환한다() {
        Reservation winner = existing(ReservationStatus.PENDING_PAYMENT, 1, PAYMENT_DEADLINE);
        given(reservationRepository.findByHoldId(HOLD_ID))
                .willReturn(Optional.empty(), Optional.of(winner));
        given(holdReader.getByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        givenCatalog(vipSeats());
        given(reservationRepository.saveIssued(any()))
                .willThrow(new ReservationAlreadyExistsException(new RuntimeException("duplicate hold")));

        CreateResult result = service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE);

        assertThat(result.created()).isFalse();
        assertThat(result.response().reservationId()).isEqualTo(RESERVATION_ID);
        verify(reservationRepository, times(2)).findByHoldId(HOLD_ID);
    }

    @Test
    void 충돌_후_재조회한_예매의_소유자도_검증한다() {
        Reservation other = existing(ReservationStatus.PENDING_PAYMENT, 1, PAYMENT_DEADLINE)
                .toBuilder().memberId(OTHER_MEMBER_ID).build();
        given(reservationRepository.findByHoldId(HOLD_ID))
                .willReturn(Optional.empty(), Optional.of(other));
        given(holdReader.getByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        givenCatalog(vipSeats());
        given(reservationRepository.saveIssued(any()))
                .willThrow(new ReservationAlreadyExistsException(new RuntimeException("duplicate hold")));

        assertThatThrownBy(() -> service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE))
                .isInstanceOf(HoldNotOwnedException.class);
    }

    @Test
    void 다른_선점의_좌석_충돌을_예매_재응답으로_바꾸지_않는다() {
        given(holdReader.getByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        givenCatalog(vipSeats());
        given(reservationRepository.saveIssued(any())).willThrow(new SeatAlreadyHeldException());

        assertThatThrownBy(() -> service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE))
                .isInstanceOf(SeatAlreadyHeldException.class);
        verify(reservationRepository).findByHoldId(HOLD_ID);
    }

    @Test
    void 일반_저장_실패를_예매_재응답으로_바꾸지_않는다() {
        given(holdReader.getByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        givenCatalog(vipSeats());
        RuntimeException failure = new IllegalStateException("storage unavailable");
        given(reservationRepository.saveIssued(any())).willThrow(failure);

        assertThatThrownBy(() -> service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE))
                .isSameAs(failure);
        verify(reservationRepository).findByHoldId(HOLD_ID);
    }
}
