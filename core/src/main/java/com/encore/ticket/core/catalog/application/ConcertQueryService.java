package com.encore.ticket.core.catalog.application;

import com.encore.ticket.core.catalog.dto.ConcertDetailResponse;
import com.encore.ticket.core.catalog.dto.ConcertSummaryResponse;
import com.encore.ticket.core.catalog.dto.PageResponse;

import java.util.List;
import java.util.Map;
import com.encore.ticket.core.catalog.domain.Concert;
import com.encore.ticket.core.catalog.domain.ConcertPeriod;
import com.encore.ticket.core.catalog.domain.ConcertPrice;
import com.encore.ticket.core.catalog.domain.ConcertSchedule;
import com.encore.ticket.core.catalog.port.ConcertLikeRepository;
import com.encore.ticket.core.catalog.port.ConcertRepository;
import com.encore.ticket.core.catalog.port.ConcertScheduleRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConcertQueryService {

    private final ConcertRepository concertRepository;
    private final ConcertScheduleRepository concertScheduleRepository;
    private final ConcertLikeRepository concertLikeRepository;

    public PageResponse<ConcertSummaryResponse> concerts(int page, int size) {
        List<Concert> concerts = concertRepository.findPage(page, size);
        List<Long> concertIds = concerts.stream().map(Concert::id).toList();

        Map<Long, List<ConcertSchedule>> schedules = concertScheduleRepository.schedulesOf(concertIds);
        Map<Long, Long> minPrices = concertScheduleRepository.minPricesOf(concertIds);

        List<ConcertSummaryResponse> content = concerts.stream()
                .map(concert -> toSummary(
                        concert,
                        ConcertPeriod.of(schedules.get(concert.id())),
                        minPrices.get(concert.id())))
                .toList();

        long totalElements = concertRepository.count();
        return new PageResponse<>(content, page, size, totalElements, totalPages(totalElements, size));
    }

    public ConcertDetailResponse detail(long concertId, Long memberId) {
        Concert concert = concertRepository.getById(concertId);
        List<ConcertSchedule> schedules = concertScheduleRepository.schedulesOf(concertId);
        List<ConcertPrice> prices = concertScheduleRepository.pricesOf(concertId);

        boolean liked = memberId != null && concertLikeRepository.exists(concertId, memberId);

        return new ConcertDetailResponse(
                concert.id(),
                concert.title(),
                concert.description(),
                concert.notice(),
                concert.posterUrl(),
                concert.venue(),
                concert.likeCount(),
                liked,
                schedules.stream().map(this::toDetailSchedule).toList(),
                prices.stream().map(this::toDetailPrice).toList());
    }

    private ConcertDetailResponse.Schedule toDetailSchedule(ConcertSchedule schedule) {
        return new ConcertDetailResponse.Schedule(
                schedule.id(),
                schedule.startsAt(),
                schedule.endsAt(),
                schedule.bookingOpensAt(),
                schedule.bookingClosesAt(),
                schedule.status());
    }

    private ConcertDetailResponse.Price toDetailPrice(ConcertPrice price) {
        return new ConcertDetailResponse.Price(price.grade(), price.price());
    }

    private ConcertSummaryResponse toSummary(Concert concert, ConcertPeriod period, Long minPrice) {
        return new ConcertSummaryResponse(
                concert.id(),
                concert.title(),
                concert.posterUrl(),
                concert.venue(),
                period.startDate(),
                period.endDate(),
                period.bookingOpensAt(),
                concert.status(),
                minPrice);
    }

    private int totalPages(long totalElements, int size) {
        return (int) ((totalElements + size - 1) / size);
    }
}
