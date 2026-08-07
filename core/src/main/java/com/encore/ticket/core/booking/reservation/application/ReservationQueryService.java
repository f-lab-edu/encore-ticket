package com.encore.ticket.core.booking.reservation.application;

import com.encore.ticket.core.booking.CompletedPayment;
import com.encore.ticket.core.booking.dto.ReservationDetailResponse;
import com.encore.ticket.core.booking.dto.ReservationSummaryResponse;
import com.encore.ticket.core.booking.exception.ReservationNotOwnedException;
import com.encore.ticket.core.catalog.port.ScheduleCatalogReader;
import com.encore.ticket.core.catalog.domain.ScheduleInfo;
import com.encore.ticket.core.catalog.port.SeatCatalogReader;
import com.encore.ticket.core.catalog.domain.SeatInfo;
import com.encore.ticket.core.catalog.dto.PageResponse;

import java.util.List;
import java.util.Map;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;


import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ReservationQueryService {

    private final ReservationRepository reservationRepository;
    private final SeatCatalogReader seatCatalogReader;
    private final ScheduleCatalogReader scheduleCatalogReader;

    public PageResponse<ReservationSummaryResponse> reservationsOf(Long memberId, int page, int size) {
        List<Reservation> reservations = reservationRepository.findPageByMemberId(memberId, page, size);
        Map<Long, ScheduleInfo> schedules = scheduleCatalogReader.schedulesOf(
                reservations.stream().map(Reservation::scheduleId).distinct().toList());

        List<ReservationSummaryResponse> content = reservations.stream()
                .map(reservation -> toSummary(reservation, schedules.get(reservation.scheduleId())))
                .toList();

        long totalElements = reservationRepository.countByMemberId(memberId);
        return new PageResponse<>(content, page, size, totalElements, totalPages(totalElements, size));
    }

    public ReservationDetailResponse detail(Long reservationId, Long memberId, CompletedPayment payment) {
        Reservation reservation = reservationRepository.getById(reservationId);
        if (!reservation.isOwnedBy(memberId)) {
            throw new ReservationNotOwnedException();
        }

        ScheduleInfo schedule = scheduleCatalogReader.scheduleOf(reservation.scheduleId());
        List<SeatInfo> seats = seatCatalogReader.seatsByIds(reservation.seatIds());

        return new ReservationDetailResponse(
                reservation.id(),
                reservation.status(),
                new ReservationDetailResponse.Concert(
                        schedule.concertId(), schedule.concertTitle(), schedule.posterUrl()),
                new ReservationDetailResponse.Schedule(
                        schedule.id(), schedule.startsAt(), schedule.venue()),
                seats.stream().map(this::toDetailSeat).toList(),
                reservation.amount(),
                payment.paymentKey(),
                payment.orderId(),
                reservation.reservedAt());
    }

    private ReservationSummaryResponse toSummary(Reservation reservation, ScheduleInfo schedule) {
        return new ReservationSummaryResponse(
                reservation.id(),
                schedule.concertTitle(),
                schedule.posterUrl(),
                schedule.startsAt(),
                schedule.venue(),
                reservation.seatIds().size(),
                reservation.amount(),
                reservation.status());
    }

    private ReservationDetailResponse.Seat toDetailSeat(SeatInfo seat) {
        return new ReservationDetailResponse.Seat(
                seat.id(), seat.section(), seat.row(), seat.number(), seat.grade(), seat.price());
    }

    private int totalPages(long totalElements, int size) {
        return (int) ((totalElements + size - 1) / size);
    }
}
