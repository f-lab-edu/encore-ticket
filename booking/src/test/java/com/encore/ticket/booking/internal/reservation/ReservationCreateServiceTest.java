package com.encore.ticket.booking.internal.reservation;

import com.encore.ticket.booking.api.PaymentAttemptState;
import com.encore.ticket.booking.api.dto.ReservationStatus;
import com.encore.ticket.booking.api.exception.HoldExpiredException;
import com.encore.ticket.booking.api.exception.HoldNotOwnedException;
import com.encore.ticket.booking.api.exception.ReservationCancelledException;
import com.encore.ticket.catalog.api.ScheduleCatalogReader;
import com.encore.ticket.catalog.api.ScheduleInfo;
import com.encore.ticket.catalog.api.SeatCatalogReader;
import com.encore.ticket.catalog.api.SeatInfo;
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
    private static final OffsetDateTime EXTENDED_EXPIRES_AT = OffsetDateTime.parse("2026-08-04T10:07:00Z");
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
                reservationRepository, holdReader, seatCatalogReader, scheduleCatalogReader, CLOCK);
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
                new SeatInfo(1001L, "A구역", "1열", "1번", "VIP", SEAT_PRICE),
                new SeatInfo(1002L, "A구역", "1열", "2번", "VIP", SEAT_PRICE));
    }

    private Reservation existing(ReservationStatus status, int paymentAttemptNo, OffsetDateTime expiresAt) {
        return Reservation.builder()
                .id(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .scheduleId(SCHEDULE_ID)
                .seatIds(SEAT_IDS)
                .amount(TOTAL_AMOUNT)
                .status(status)
                .expiresAt(expiresAt)
                .originalExpiresAt(HOLD_EXPIRES_AT)
                .performanceStartsAt(PERFORMANCE_STARTS_AT)
                .reservedAt(OffsetDateTime.parse("2026-08-04T09:58:00Z"))
                .paymentAttemptNo(paymentAttemptNo)
                .build();
    }

    @Test
    void 최초_요청이면_선점을_승계한_결제_대기_예매를_만든다() {
        given(holdReader.findByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        given(reservationRepository.findByHoldId(HOLD_ID)).willReturn(Optional.empty());
        givenCatalog(vipSeats());
        given(reservationRepository.save(any()))
                .willReturn(existing(ReservationStatus.PENDING_PAYMENT, 1, HOLD_EXPIRES_AT));

        CreateResult result = service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE);

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        Reservation created = captor.getValue();

        assertThat(created.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(created.amount()).isEqualTo(TOTAL_AMOUNT);
        assertThat(created.scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(created.seatIds()).containsExactly(1001L, 1002L);
        assertThat(created.expiresAt()).isEqualTo(HOLD_EXPIRES_AT);
        assertThat(created.originalExpiresAt()).isEqualTo(HOLD_EXPIRES_AT);
        assertThat(created.reservedAt()).isEqualTo(OffsetDateTime.parse("2026-08-04T10:00:00Z"));
        assertThat(created.paymentAttemptNo()).isEqualTo(1);

        assertThat(result.created()).isTrue();
        assertThat(result.response().reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(result.response().orderId()).isEqualTo("reservation-501-1");
        assertThat(result.response().amount()).isEqualTo(TOTAL_AMOUNT);
        assertThat(result.response().status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(result.response().expiresAt()).isEqualTo(HOLD_EXPIRES_AT);
        assertThat(result.response().originalExpiresAt()).isEqualTo(HOLD_EXPIRES_AT);
    }

    @Test
    void 주문명은_콘서트명과_등급과_나머지_매수를_담는다() {
        given(holdReader.findByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        given(reservationRepository.findByHoldId(HOLD_ID)).willReturn(Optional.empty());
        givenCatalog(vipSeats());
        given(reservationRepository.save(any()))
                .willReturn(existing(ReservationStatus.PENDING_PAYMENT, 1, HOLD_EXPIRES_AT));

        CreateResult result = service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE);

        assertThat(result.response().orderName()).isEqualTo("2026 아이유 콘서트 VIP석 외 1매");
    }

    @Test
    void 좌석이_한_장이면_주문명에_나머지_매수를_붙이지_않는다() {
        given(holdReader.findByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        given(reservationRepository.findByHoldId(HOLD_ID)).willReturn(Optional.empty());
        givenCatalog(List.of(new SeatInfo(1001L, "A구역", "1열", "1번", "VIP", SEAT_PRICE)));
        given(reservationRepository.save(any()))
                .willReturn(existing(ReservationStatus.PENDING_PAYMENT, 1, HOLD_EXPIRES_AT));

        CreateResult result = service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE);

        assertThat(result.response().orderName()).isEqualTo("2026 아이유 콘서트 VIP석");
    }

    @Test
    void 다른_사용자의_선점으로_예매를_생성하면_실패한다() {
        given(holdReader.findByHoldId(HOLD_ID)).willReturn(hold(OTHER_MEMBER_ID, HOLD_EXPIRES_AT));

        assertThatThrownBy(() -> service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE))
                .isInstanceOf(HoldNotOwnedException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 선점_만료_시각에_도달한_뒤_예매를_생성하면_실패한다() {
        given(holdReader.findByHoldId(HOLD_ID))
                .willReturn(hold(MEMBER_ID, OffsetDateTime.parse("2026-08-04T10:00:00Z")));
        given(reservationRepository.findByHoldId(HOLD_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE))
                .isInstanceOf(HoldExpiredException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 결제_시도가_없던_예매로_재요청하면_기존_주문번호를_그대로_돌려준다() {
        given(holdReader.findByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        given(reservationRepository.findByHoldId(HOLD_ID))
                .willReturn(Optional.of(existing(ReservationStatus.PENDING_PAYMENT, 1, EXTENDED_EXPIRES_AT)));
        givenCatalog(vipSeats());

        CreateResult result = service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE);

        assertThat(result.created()).isFalse();
        assertThat(result.response().orderId()).isEqualTo("reservation-501-1");
        assertThat(result.response().expiresAt()).isEqualTo(EXTENDED_EXPIRES_AT);
        assertThat(result.response().originalExpiresAt()).isEqualTo(HOLD_EXPIRES_AT);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 결제가_진행_중이면_새_주문번호를_발급하지_않는다() {
        given(holdReader.findByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
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
        given(holdReader.findByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        given(reservationRepository.findByHoldId(HOLD_ID)).willReturn(Optional.of(reservation));
        givenCatalog(vipSeats());

        CreateResult result = service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.FAILED);

        assertThat(result.created()).isFalse();
        assertThat(result.response().orderId()).isEqualTo("reservation-501-2");

        verify(reservationRepository).save(reservation);
    }

    @Test
    void 확정된_예매로_재요청하면_확정_상태를_그대로_돌려준다() {
        given(holdReader.findByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        given(reservationRepository.findByHoldId(HOLD_ID))
                .willReturn(Optional.of(existing(ReservationStatus.CONFIRMED, 1, HOLD_EXPIRES_AT)));
        givenCatalog(vipSeats());

        CreateResult result = service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.COMPLETED);

        assertThat(result.created()).isFalse();
        assertThat(result.response().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(result.response().orderId()).isEqualTo("reservation-501-1");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 취소된_예매의_선점으로_재요청하면_실패한다() {
        given(holdReader.findByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        given(reservationRepository.findByHoldId(HOLD_ID))
                .willReturn(Optional.of(existing(ReservationStatus.CANCELLED, 1, HOLD_EXPIRES_AT)));

        assertThatThrownBy(() -> service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE))
                .isInstanceOf(ReservationCancelledException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 만료된_예매의_선점으로_재요청하면_실패한다() {
        given(holdReader.findByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        given(reservationRepository.findByHoldId(HOLD_ID))
                .willReturn(Optional.of(existing(ReservationStatus.EXPIRED, 1, HOLD_EXPIRES_AT)));

        assertThatThrownBy(() -> service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE))
                .isInstanceOf(HoldExpiredException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 예매_만료_시각에_도달했으면_재요청도_실패한다() {
        given(holdReader.findByHoldId(HOLD_ID)).willReturn(hold(MEMBER_ID, HOLD_EXPIRES_AT));
        given(reservationRepository.findByHoldId(HOLD_ID)).willReturn(Optional.of(existing(
                ReservationStatus.PENDING_PAYMENT, 1, OffsetDateTime.parse("2026-08-04T10:00:00Z"))));

        assertThatThrownBy(() -> service.create(HOLD_ID, MEMBER_ID, PaymentAttemptState.NONE))
                .isInstanceOf(HoldExpiredException.class);

        verify(reservationRepository, never()).save(any());
    }
}
