package com.encore.ticket.catalog.internal.concert;

import com.encore.ticket.catalog.api.dto.ConcertDetailResponse;
import com.encore.ticket.catalog.api.dto.ConcertStatus;
import com.encore.ticket.catalog.api.dto.ConcertSummaryResponse;
import com.encore.ticket.catalog.api.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConcertQueryServiceTest {

    private static final long CONCERT_ID = 1L;
    private static final long OTHER_CONCERT_ID = 2L;
    private static final long MEMBER_ID = 100L;

    private static final OffsetDateTime FIRST_SHOW = OffsetDateTime.parse("2026-09-01T09:00:00Z");
    private static final OffsetDateTime LAST_SHOW = OffsetDateTime.parse("2026-09-03T09:00:00Z");
    private static final OffsetDateTime FIRST_SHOW_OPENS_AT = OffsetDateTime.parse("2026-08-01T11:00:00Z");
    private static final OffsetDateTime LAST_SHOW_OPENS_AT = OffsetDateTime.parse("2026-07-25T11:00:00Z");

    @Mock ConcertRepository concertRepository;
    @Mock ConcertScheduleRepository concertScheduleRepository;
    @Mock ConcertLikeRepository concertLikeRepository;

    ConcertQueryService service;

    @BeforeEach
    void setUp() {
        service = new ConcertQueryService(concertRepository, concertScheduleRepository, concertLikeRepository);
    }

    private Concert concert(long id, String title) {
        return Concert.builder()
                .id(id)
                .title(title)
                .description("2026년 아이유 단독 콘서트입니다.")
                .notice("회차별 1인 4매까지 예매할 수 있습니다.")
                .posterUrl("https://example.com/poster.jpg")
                .venue("KSPO DOME")
                .status(ConcertStatus.ON_SALE)
                .likeCount(128)
                .build();
    }

    private ConcertSchedule show(long id, OffsetDateTime startsAt, OffsetDateTime bookingOpensAt,
                                 ConcertStatus status) {
        return new ConcertSchedule(
                id, startsAt, startsAt.plusHours(2), bookingOpensAt, startsAt.minusHours(1), status);
    }

    private List<ConcertSchedule> twoShows() {
        return List.of(
                show(101L, FIRST_SHOW, FIRST_SHOW_OPENS_AT, ConcertStatus.ON_SALE),
                show(102L, LAST_SHOW, LAST_SHOW_OPENS_AT, ConcertStatus.SOLD_OUT));
    }

    @Test
    void 목록은_콘서트와_회차와_가격을_합쳐_카드로_돌려준다() {
        given(concertRepository.findPage(0, 12)).willReturn(List.of(concert(CONCERT_ID, "2026 아이유 콘서트")));
        given(concertScheduleRepository.schedulesOf(List.of(CONCERT_ID)))
                .willReturn(Map.of(CONCERT_ID, twoShows()));
        given(concertScheduleRepository.minPricesOf(List.of(CONCERT_ID)))
                .willReturn(Map.of(CONCERT_ID, 121_000L));
        given(concertRepository.count()).willReturn(1L);

        PageResponse<ConcertSummaryResponse> response = service.concerts(0, 12);

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(12);
        assertThat(response.totalElements()).isEqualTo(1L);
        assertThat(response.totalPages()).isEqualTo(1);

        ConcertSummaryResponse card = response.content().getFirst();
        assertThat(card.id()).isEqualTo(CONCERT_ID);
        assertThat(card.title()).isEqualTo("2026 아이유 콘서트");
        assertThat(card.posterUrl()).isEqualTo("https://example.com/poster.jpg");
        assertThat(card.venue()).isEqualTo("KSPO DOME");
        assertThat(card.status()).isEqualTo(ConcertStatus.ON_SALE);
        assertThat(card.minPrice()).isEqualTo(121_000L);
    }

    @Test
    void 공연_기간은_가장_이른_회차와_가장_늦은_회차의_날짜다() {
        given(concertRepository.findPage(0, 12)).willReturn(List.of(concert(CONCERT_ID, "2026 아이유 콘서트")));
        given(concertScheduleRepository.schedulesOf(List.of(CONCERT_ID)))
                .willReturn(Map.of(CONCERT_ID, twoShows()));
        given(concertScheduleRepository.minPricesOf(List.of(CONCERT_ID)))
                .willReturn(Map.of(CONCERT_ID, 121_000L));
        given(concertRepository.count()).willReturn(1L);

        ConcertSummaryResponse card = service.concerts(0, 12).content().getFirst();

        assertThat(card.performanceStartDate()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(card.performanceEndDate()).isEqualTo(LocalDate.parse("2026-09-03"));
    }

    @Test
    void 예매_오픈_시각은_가장_이른_회차의_것이지_가장_이른_오픈_시각이_아니다() {
        given(concertRepository.findPage(0, 12)).willReturn(List.of(concert(CONCERT_ID, "2026 아이유 콘서트")));
        given(concertScheduleRepository.schedulesOf(List.of(CONCERT_ID)))
                .willReturn(Map.of(CONCERT_ID, twoShows()));
        given(concertScheduleRepository.minPricesOf(List.of(CONCERT_ID)))
                .willReturn(Map.of(CONCERT_ID, 121_000L));
        given(concertRepository.count()).willReturn(1L);

        ConcertSummaryResponse card = service.concerts(0, 12).content().getFirst();

        assertThat(card.bookingOpensAt()).isEqualTo(FIRST_SHOW_OPENS_AT);
        assertThat(LAST_SHOW_OPENS_AT).isBefore(FIRST_SHOW_OPENS_AT);
    }

    @Test
    void 회차와_가격을_콘서트마다_따로_조회하지_않는다() {
        given(concertRepository.findPage(0, 12)).willReturn(List.of(
                concert(CONCERT_ID, "2026 아이유 콘서트"),
                concert(OTHER_CONCERT_ID, "2026 악뮤 콘서트")));
        given(concertScheduleRepository.schedulesOf(List.of(CONCERT_ID, OTHER_CONCERT_ID))).willReturn(Map.of(
                CONCERT_ID, twoShows(),
                OTHER_CONCERT_ID, List.of(show(201L, LAST_SHOW, LAST_SHOW_OPENS_AT, ConcertStatus.ON_SALE))));
        given(concertScheduleRepository.minPricesOf(List.of(CONCERT_ID, OTHER_CONCERT_ID))).willReturn(Map.of(
                CONCERT_ID, 121_000L,
                OTHER_CONCERT_ID, 99_000L));
        given(concertRepository.count()).willReturn(2L);

        PageResponse<ConcertSummaryResponse> response = service.concerts(0, 12);

        assertThat(response.content())
                .extracting(ConcertSummaryResponse::id, ConcertSummaryResponse::title,
                        ConcertSummaryResponse::minPrice, ConcertSummaryResponse::bookingOpensAt)
                .containsExactly(
                        tuple(CONCERT_ID, "2026 아이유 콘서트", 121_000L, FIRST_SHOW_OPENS_AT),
                        tuple(OTHER_CONCERT_ID, "2026 악뮤 콘서트", 99_000L, LAST_SHOW_OPENS_AT));
    }

    @Test
    void 전체_페이지_수는_마지막_페이지를_올려서_센다() {
        given(concertRepository.findPage(1, 4)).willReturn(List.of(concert(CONCERT_ID, "2026 아이유 콘서트")));
        given(concertScheduleRepository.schedulesOf(List.of(CONCERT_ID)))
                .willReturn(Map.of(CONCERT_ID, twoShows()));
        given(concertScheduleRepository.minPricesOf(List.of(CONCERT_ID)))
                .willReturn(Map.of(CONCERT_ID, 121_000L));
        given(concertRepository.count()).willReturn(10L);

        PageResponse<ConcertSummaryResponse> response = service.concerts(1, 4);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(4);
        assertThat(response.totalElements()).isEqualTo(10L);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void 콘서트가_없으면_빈_목록과_0페이지를_돌려준다() {
        given(concertRepository.findPage(0, 12)).willReturn(List.of());
        given(concertScheduleRepository.schedulesOf(List.of())).willReturn(Map.of());
        given(concertScheduleRepository.minPricesOf(List.of())).willReturn(Map.of());
        given(concertRepository.count()).willReturn(0L);

        PageResponse<ConcertSummaryResponse> response = service.concerts(0, 12);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    private void givenDetailOf(Concert concert) {
        given(concertRepository.findById(CONCERT_ID)).willReturn(concert);
        given(concertScheduleRepository.schedulesOf(CONCERT_ID)).willReturn(twoShows());
        given(concertScheduleRepository.pricesOf(CONCERT_ID)).willReturn(List.of(
                new ConcertPrice("VIP", 165_000L),
                new ConcertPrice("R", 143_000L)));
    }

    @Test
    void 상세는_콘서트와_회차와_가격을_합쳐_돌려준다() {
        givenDetailOf(concert(CONCERT_ID, "2026 아이유 콘서트"));

        ConcertDetailResponse response = service.detail(CONCERT_ID, null);

        assertThat(response.id()).isEqualTo(CONCERT_ID);
        assertThat(response.title()).isEqualTo("2026 아이유 콘서트");
        assertThat(response.description()).isEqualTo("2026년 아이유 단독 콘서트입니다.");
        assertThat(response.notice()).isEqualTo("회차별 1인 4매까지 예매할 수 있습니다.");
        assertThat(response.posterUrl()).isEqualTo("https://example.com/poster.jpg");
        assertThat(response.venue()).isEqualTo("KSPO DOME");
        assertThat(response.likeCount()).isEqualTo(128);

        assertThat(response.prices())
                .extracting(ConcertDetailResponse.Price::grade, ConcertDetailResponse.Price::price)
                .containsExactly(tuple("VIP", 165_000L), tuple("R", 143_000L));
    }

    @Test
    void 상세의_회차는_시각_네_개와_회차별_상태를_담는다() {
        givenDetailOf(concert(CONCERT_ID, "2026 아이유 콘서트"));

        ConcertDetailResponse response = service.detail(CONCERT_ID, null);

        assertThat(response.schedules())
                .extracting(ConcertDetailResponse.Schedule::id, ConcertDetailResponse.Schedule::startsAt,
                        ConcertDetailResponse.Schedule::endsAt, ConcertDetailResponse.Schedule::bookingOpensAt,
                        ConcertDetailResponse.Schedule::bookingClosesAt, ConcertDetailResponse.Schedule::status)
                .containsExactly(
                        tuple(101L, FIRST_SHOW, FIRST_SHOW.plusHours(2), FIRST_SHOW_OPENS_AT,
                                FIRST_SHOW.minusHours(1), ConcertStatus.ON_SALE),
                        tuple(102L, LAST_SHOW, LAST_SHOW.plusHours(2), LAST_SHOW_OPENS_AT,
                                LAST_SHOW.minusHours(1), ConcertStatus.SOLD_OUT));
    }

    @Test
    void 미로그인이면_좋아요_여부를_묻지_않고_거짓으로_돌려준다() {
        givenDetailOf(concert(CONCERT_ID, "2026 아이유 콘서트"));

        ConcertDetailResponse response = service.detail(CONCERT_ID, null);

        assertThat(response.liked()).isFalse();

        verify(concertLikeRepository, never()).exists(anyLong(), anyLong());
    }

    @Test
    void 좋아요한_사용자가_조회하면_liked가_참이다() {
        givenDetailOf(concert(CONCERT_ID, "2026 아이유 콘서트"));
        given(concertLikeRepository.exists(CONCERT_ID, MEMBER_ID)).willReturn(true);

        ConcertDetailResponse response = service.detail(CONCERT_ID, MEMBER_ID);

        assertThat(response.liked()).isTrue();
    }

    @Test
    void 좋아요하지_않은_사용자가_조회하면_liked가_거짓이다() {
        givenDetailOf(concert(CONCERT_ID, "2026 아이유 콘서트"));
        given(concertLikeRepository.exists(CONCERT_ID, MEMBER_ID)).willReturn(false);

        ConcertDetailResponse response = service.detail(CONCERT_ID, MEMBER_ID);

        assertThat(response.liked()).isFalse();
    }

    @Test
    void 공지사항이_없는_콘서트도_상세를_돌려준다() {
        givenDetailOf(Concert.builder()
                .id(CONCERT_ID)
                .title("2026 아이유 콘서트")
                .description("2026년 아이유 단독 콘서트입니다.")
                .posterUrl("https://example.com/poster.jpg")
                .venue("KSPO DOME")
                .status(ConcertStatus.ON_SALE)
                .likeCount(128)
                .build());

        ConcertDetailResponse response = service.detail(CONCERT_ID, null);

        assertThat(response.notice()).isNull();
        assertThat(response.title()).isEqualTo("2026 아이유 콘서트");
    }
}
