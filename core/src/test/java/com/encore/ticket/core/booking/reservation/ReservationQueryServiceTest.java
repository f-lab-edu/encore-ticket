package com.encore.ticket.core.booking.reservation;

import com.encore.ticket.core.booking.CompletedPayment;
import com.encore.ticket.core.booking.dto.ReservationDetailResponse;
import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.booking.dto.ReservationSummaryResponse;
import com.encore.ticket.core.booking.exception.ReservationNotOwnedException;
import com.encore.ticket.core.catalog.ScheduleCatalogReader;
import com.encore.ticket.core.catalog.ScheduleInfo;
import com.encore.ticket.core.catalog.SeatCatalogReader;
import com.encore.ticket.core.catalog.SeatInfo;
import com.encore.ticket.core.catalog.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationQueryServiceTest {

    private static final long MEMBER_ID = 100L;
    private static final long OTHER_MEMBER_ID = 200L;
    private static final long SCHEDULE_ID = 101L;
    private static final long OTHER_SCHEDULE_ID = 102L;
    private static final long RESERVATION_ID = 501L;
    private static final long SEAT_PRICE = 165_000L;
    private static final long TOTAL_AMOUNT = 330_000L;
    private static final OffsetDateTime PERFORMANCE_STARTS_AT = OffsetDateTime.parse("2026-09-01T09:00:00Z");
    private static final OffsetDateTime RESERVED_AT = OffsetDateTime.parse("2026-08-04T09:58:00Z");

    @Mock ReservationRepository reservationRepository;
    @Mock SeatCatalogReader seatCatalogReader;
    @Mock ScheduleCatalogReader scheduleCatalogReader;

    ReservationQueryService service;

    @BeforeEach
    void setUp() {
        service = new ReservationQueryService(reservationRepository, seatCatalogReader, scheduleCatalogReader);
    }

    private Reservation reservation(long id, long scheduleId, List<Long> seatIds, ReservationStatus status) {
        return Reservation.builder()
                .id(id)
                .memberId(MEMBER_ID)
                .scheduleId(scheduleId)
                .seatIds(seatIds)
                .amount(TOTAL_AMOUNT)
                .status(status)
                .performanceStartsAt(PERFORMANCE_STARTS_AT)
                .reservedAt(RESERVED_AT)
                .paymentAttemptNo(1)
                .build();
    }

    private ScheduleInfo schedule(long scheduleId, String concertTitle) {
        return new ScheduleInfo(scheduleId, PERFORMANCE_STARTS_AT, "KSPO DOME",
                1L, concertTitle, "https://example.com/poster.jpg");
    }

    private List<SeatInfo> vipSeats() {
        return List.of(
                new SeatInfo(1001L, "A구역", "1열", "1번", "VIP", SEAT_PRICE),
                new SeatInfo(1002L, "A구역", "1열", "2번", "VIP", SEAT_PRICE));
    }

    @Test
    void 목록은_예매와_회차_정보를_합쳐_카드로_돌려준다() {
        given(reservationRepository.findPageByMemberId(MEMBER_ID, 0, 10)).willReturn(List.of(
                reservation(RESERVATION_ID, SCHEDULE_ID, List.of(1001L, 1002L), ReservationStatus.CONFIRMED)));
        given(scheduleCatalogReader.schedulesOf(List.of(SCHEDULE_ID)))
                .willReturn(Map.of(SCHEDULE_ID, schedule(SCHEDULE_ID, "2026 아이유 콘서트")));
        given(reservationRepository.countByMemberId(MEMBER_ID)).willReturn(1L);

        PageResponse<ReservationSummaryResponse> response = service.reservationsOf(MEMBER_ID, 0, 10);

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(1L);
        assertThat(response.totalPages()).isEqualTo(1);

        ReservationSummaryResponse card = response.content().getFirst();
        assertThat(card.id()).isEqualTo(RESERVATION_ID);
        assertThat(card.concertTitle()).isEqualTo("2026 아이유 콘서트");
        assertThat(card.posterUrl()).isEqualTo("https://example.com/poster.jpg");
        assertThat(card.startsAt()).isEqualTo(PERFORMANCE_STARTS_AT);
        assertThat(card.venue()).isEqualTo("KSPO DOME");
        assertThat(card.seatCount()).isEqualTo(2);
        assertThat(card.totalAmount()).isEqualTo(TOTAL_AMOUNT);
        assertThat(card.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void 목록은_회차를_예매마다_따로_조회하지_않는다() {
        given(reservationRepository.findPageByMemberId(MEMBER_ID, 0, 10)).willReturn(List.of(
                reservation(503L, SCHEDULE_ID, List.of(1001L), ReservationStatus.CONFIRMED),
                reservation(502L, SCHEDULE_ID, List.of(1002L), ReservationStatus.CANCELLED),
                reservation(501L, OTHER_SCHEDULE_ID, List.of(2001L), ReservationStatus.PENDING_PAYMENT)));
        given(scheduleCatalogReader.schedulesOf(List.of(SCHEDULE_ID, OTHER_SCHEDULE_ID))).willReturn(Map.of(
                SCHEDULE_ID, schedule(SCHEDULE_ID, "2026 아이유 콘서트"),
                OTHER_SCHEDULE_ID, schedule(OTHER_SCHEDULE_ID, "2026 악뮤 콘서트")));
        given(reservationRepository.countByMemberId(MEMBER_ID)).willReturn(3L);

        PageResponse<ReservationSummaryResponse> response = service.reservationsOf(MEMBER_ID, 0, 10);

        assertThat(response.content())
                .extracting(ReservationSummaryResponse::id, ReservationSummaryResponse::concertTitle,
                        ReservationSummaryResponse::status)
                .containsExactly(
                        tuple(503L, "2026 아이유 콘서트", ReservationStatus.CONFIRMED),
                        tuple(502L, "2026 아이유 콘서트", ReservationStatus.CANCELLED),
                        tuple(501L, "2026 악뮤 콘서트", ReservationStatus.PENDING_PAYMENT));

        verify(scheduleCatalogReader, never()).scheduleOf(anyLong());
    }

    @Test
    void 전체_페이지_수는_마지막_페이지를_올려서_센다() {
        given(reservationRepository.findPageByMemberId(MEMBER_ID, 1, 2)).willReturn(List.of(
                reservation(503L, SCHEDULE_ID, List.of(1001L), ReservationStatus.CONFIRMED),
                reservation(502L, SCHEDULE_ID, List.of(1002L), ReservationStatus.CONFIRMED)));
        given(scheduleCatalogReader.schedulesOf(List.of(SCHEDULE_ID)))
                .willReturn(Map.of(SCHEDULE_ID, schedule(SCHEDULE_ID, "2026 아이유 콘서트")));
        given(reservationRepository.countByMemberId(MEMBER_ID)).willReturn(5L);

        PageResponse<ReservationSummaryResponse> response = service.reservationsOf(MEMBER_ID, 1, 2);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5L);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void 예매가_없으면_빈_목록과_0페이지를_돌려준다() {
        given(reservationRepository.findPageByMemberId(MEMBER_ID, 0, 10)).willReturn(List.of());
        given(scheduleCatalogReader.schedulesOf(List.of())).willReturn(Map.of());
        given(reservationRepository.countByMemberId(MEMBER_ID)).willReturn(0L);

        PageResponse<ReservationSummaryResponse> response = service.reservationsOf(MEMBER_ID, 0, 10);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void 내역은_콘서트와_회차와_좌석을_합쳐_돌려준다() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(
                reservation(RESERVATION_ID, SCHEDULE_ID, List.of(1001L, 1002L), ReservationStatus.CONFIRMED));
        given(scheduleCatalogReader.scheduleOf(SCHEDULE_ID)).willReturn(schedule(SCHEDULE_ID, "2026 아이유 콘서트"));
        given(seatCatalogReader.seatsByIds(List.of(1001L, 1002L))).willReturn(vipSeats());

        ReservationDetailResponse response = service.detail(
                RESERVATION_ID, MEMBER_ID, new CompletedPayment("payment-key", "reservation-501-1"));

        assertThat(response.id()).isEqualTo(RESERVATION_ID);
        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(response.concert().id()).isEqualTo(1L);
        assertThat(response.concert().title()).isEqualTo("2026 아이유 콘서트");
        assertThat(response.concert().posterUrl()).isEqualTo("https://example.com/poster.jpg");
        assertThat(response.schedule().id()).isEqualTo(SCHEDULE_ID);
        assertThat(response.schedule().startsAt()).isEqualTo(PERFORMANCE_STARTS_AT);
        assertThat(response.schedule().venue()).isEqualTo("KSPO DOME");
        assertThat(response.totalAmount()).isEqualTo(TOTAL_AMOUNT);
        assertThat(response.reservedAt()).isEqualTo(RESERVED_AT);

        assertThat(response.seats())
                .extracting(ReservationDetailResponse.Seat::id, ReservationDetailResponse.Seat::number,
                        ReservationDetailResponse.Seat::grade, ReservationDetailResponse.Seat::price)
                .containsExactly(
                        tuple(1001L, "1번", "VIP", SEAT_PRICE),
                        tuple(1002L, "2번", "VIP", SEAT_PRICE));
    }

    @Test
    void 결제가_완료된_내역은_결제_키와_주문_ID를_담는다() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(
                reservation(RESERVATION_ID, SCHEDULE_ID, List.of(1001L), ReservationStatus.CONFIRMED));
        given(scheduleCatalogReader.scheduleOf(SCHEDULE_ID)).willReturn(schedule(SCHEDULE_ID, "2026 아이유 콘서트"));
        given(seatCatalogReader.seatsByIds(List.of(1001L))).willReturn(vipSeats());

        ReservationDetailResponse response = service.detail(
                RESERVATION_ID, MEMBER_ID, new CompletedPayment("payment-key", "reservation-501-1"));

        assertThat(response.paymentKey()).isEqualTo("payment-key");
        assertThat(response.orderId()).isEqualTo("reservation-501-1");
    }

    @Test
    void 결제_전_내역의_결제_키와_주문_ID는_비어_있다() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(
                reservation(RESERVATION_ID, SCHEDULE_ID, List.of(1001L), ReservationStatus.PENDING_PAYMENT));
        given(scheduleCatalogReader.scheduleOf(SCHEDULE_ID)).willReturn(schedule(SCHEDULE_ID, "2026 아이유 콘서트"));
        given(seatCatalogReader.seatsByIds(List.of(1001L))).willReturn(vipSeats());

        ReservationDetailResponse response = service.detail(RESERVATION_ID, MEMBER_ID, CompletedPayment.NONE);

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(response.paymentKey()).isNull();
        assertThat(response.orderId()).isNull();
    }

    @Test
    void 다른_사용자의_예매_내역을_조회하면_실패한다() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(
                reservation(RESERVATION_ID, SCHEDULE_ID, List.of(1001L), ReservationStatus.CONFIRMED));

        assertThatThrownBy(() -> service.detail(RESERVATION_ID, OTHER_MEMBER_ID, CompletedPayment.NONE))
                .isInstanceOf(ReservationNotOwnedException.class);

        verify(seatCatalogReader, never()).seatsByIds(any());
    }
}
