package com.encore.ticket.concert.controller;

import com.encore.ticket.core.catalog.dto.ConcertDetailResponse;
import com.encore.ticket.core.catalog.dto.ConcertLikeResponse;
import com.encore.ticket.core.catalog.dto.ConcertRankingResponse;
import com.encore.ticket.core.catalog.dto.ConcertStatus;
import com.encore.ticket.core.catalog.dto.ConcertSummaryResponse;
import com.encore.ticket.core.catalog.dto.PageResponse;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class StubConcertCatalog {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private static final int CONCERT_COUNT = 10;

    private static final LocalDate FIRST_PERFORMANCE_DATE = LocalDate.of(2026, 9, 1);

    private static final OffsetDateTime RANKING_AS_OF = OffsetDateTime.of(2026, 7, 30, 3, 0, 0, 0, KST);

    private static final long BASE_MIN_PRICE = 77_000L;

    private static final Map<Long, StubConcert> CONCERTS = createConcerts();

    private static final Map<Long, Integer> LIKE_COUNTS = createLikeCounts();

    private static final Map<Long, Set<Long>> LIKES = new ConcurrentHashMap<>();

    private StubConcertCatalog() {
    }

    static boolean exists(long concertId) {
        return CONCERTS.containsKey(concertId);
    }

    static PageResponse<ConcertSummaryResponse> page(int page, int size) {
        List<ConcertSummaryResponse> content = CONCERTS.values().stream()
                .skip((long) page * size)
                .limit(size)
                .map(StubConcertCatalog::toSummary)
                .toList();

        return new PageResponse<>(content, page, size, CONCERT_COUNT, totalPages(size));
    }

    static Optional<ConcertDetailResponse> detail(long concertId, Long memberId) {
        return Optional.ofNullable(CONCERTS.get(concertId))
                .map(concert -> toDetail(concert, isLiked(concert.id(), memberId)));
    }

    static ConcertRankingResponse ranking(int limit) {
        List<ConcertRankingResponse.Item> items = CONCERTS.values().stream()
                .limit(limit)
                .map(StubConcertCatalog::toRankingItem)
                .toList();

        return new ConcertRankingResponse(RANKING_AS_OF, items);
    }

    static LikeResult like(long concertId, long memberId) {
        boolean created = likedConcerts(memberId).add(concertId);
        if (created) {
            LIKE_COUNTS.merge(concertId, 1, Integer::sum);
        }

        return new LikeResult(created, likeResponse(concertId, true));
    }

    static ConcertLikeResponse unlike(long concertId, long memberId) {
        Set<Long> likedConcerts = LIKES.get(memberId);
        if (likedConcerts != null && likedConcerts.remove(concertId)) {
            LIKE_COUNTS.computeIfPresent(concertId, (id, likeCount) -> likeCount - 1);
        }

        return likeResponse(concertId, false);
    }

    static void reset() {
        LIKE_COUNTS.clear();
        LIKE_COUNTS.putAll(createLikeCounts());

        LIKES.clear();
    }

    private static boolean isLiked(long concertId, Long memberId) {
        return memberId != null && LIKES.getOrDefault(memberId, Set.of()).contains(concertId);
    }

    private static Set<Long> likedConcerts(long memberId) {
        return LIKES.computeIfAbsent(memberId, id -> ConcurrentHashMap.newKeySet());
    }

    private static ConcertLikeResponse likeResponse(long concertId, boolean liked) {
        return new ConcertLikeResponse(concertId, liked, likeCountOf(concertId));
    }

    private static int likeCountOf(long concertId) {
        return LIKE_COUNTS.getOrDefault(concertId, 0);
    }

    private static int totalPages(int size) {
        return (CONCERT_COUNT + size - 1) / size;
    }

    private static ConcertSummaryResponse toSummary(StubConcert concert) {
        return new ConcertSummaryResponse(
                concert.id(),
                concert.title(),
                concert.posterUrl(),
                concert.venue(),
                concert.performanceStartDate(),
                concert.performanceEndDate(),
                concert.bookingOpensAt(),
                concert.status(),
                concert.minPrice());
    }

    private static ConcertDetailResponse toDetail(StubConcert concert, boolean liked) {
        return new ConcertDetailResponse(
                concert.id(),
                concert.title(),
                concert.description(),
                concert.notice(),
                concert.posterUrl(),
                concert.venue(),
                likeCountOf(concert.id()),
                liked,
                schedulesOf(concert),
                pricesOf(concert));
    }

    private static ConcertRankingResponse.Item toRankingItem(StubConcert concert) {
        int rank = (int) concert.id();
        return new ConcertRankingResponse.Item(
                rank,
                concert.id(),
                concert.title(),
                concert.posterUrl(),
                1_000 - rank * 10);
    }

    private static List<ConcertDetailResponse.Schedule> schedulesOf(StubConcert concert) {
        return List.of(
                scheduleOf(concert, 1, concert.performanceStartDate()),
                scheduleOf(concert, 2, concert.performanceEndDate()));
    }

    private static ConcertDetailResponse.Schedule scheduleOf(StubConcert concert, int sequence, LocalDate date) {
        OffsetDateTime startsAt = date.atTime(19, 0).atOffset(KST);
        return new ConcertDetailResponse.Schedule(
                concert.id() * 100 + sequence,
                startsAt,
                startsAt.plusHours(2),
                concert.bookingOpensAt(),
                startsAt.minusDays(1),
                concert.status());
    }

    private static List<ConcertDetailResponse.Price> pricesOf(StubConcert concert) {
        return List.of(
                new ConcertDetailResponse.Price("VIP", concert.minPrice() + 100_000L),
                new ConcertDetailResponse.Price("R", concert.minPrice() + 50_000L),
                new ConcertDetailResponse.Price("S", concert.minPrice()));
    }

    private static Map<Long, StubConcert> createConcerts() {
        Map<Long, StubConcert> concerts = new LinkedHashMap<>();
        for (long id = 1; id <= CONCERT_COUNT; id++) {
            concerts.put(id, createConcert(id));
        }
        return Collections.unmodifiableMap(concerts);
    }

    private static Map<Long, Integer> createLikeCounts() {
        Map<Long, Integer> likeCounts = new ConcurrentHashMap<>();
        CONCERTS.forEach((id, concert) -> likeCounts.put(id, concert.likeCount()));
        return likeCounts;
    }

    private static StubConcert createConcert(long id) {
        int index = (int) (id - 1);
        LocalDate startDate = FIRST_PERFORMANCE_DATE.plusWeeks(index);
        ConcertStatus[] statuses = ConcertStatus.values();

        return new StubConcert(
                id,
                "스텁 콘서트 " + id,
                "스텁 콘서트 " + id + "의 상세 소개입니다.",
                id % 2 == 1 ? "공연 시작 30분 전까지 입장해 주세요." : null,
                "https://cdn.encore-ticket.test/posters/" + id + ".jpg",
                "스텁 공연장 " + id,
                startDate,
                startDate.plusDays(1),
                startDate.minusMonths(1).atTime(10, 0).atOffset(KST),
                statuses[index % statuses.length],
                BASE_MIN_PRICE + index * 1_000L,
                100 - index);
    }

    record LikeResult(
            boolean created,
            ConcertLikeResponse response) {
    }

    private record StubConcert(
            long id,
            String title,
            String description,
            String notice,
            String posterUrl,
            String venue,
            LocalDate performanceStartDate,
            LocalDate performanceEndDate,
            OffsetDateTime bookingOpensAt,
            ConcertStatus status,
            long minPrice,
            int likeCount) {
    }
}
