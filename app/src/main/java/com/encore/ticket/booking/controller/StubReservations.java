package com.encore.ticket.booking.controller;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.encore.ticket.core.booking.dto.ReservationCancelResponse;
import com.encore.ticket.core.booking.dto.ReservationDetailResponse;
import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.booking.dto.ReservationSummaryResponse;
import com.encore.ticket.core.booking.dto.SeatMapResponse;
import com.encore.ticket.core.catalog.dto.PageResponse;

final class StubReservations {

    static final long PENDING_RESERVATION_ID = 501L;

    static final long CONFIRMED_RESERVATION_ID = 502L;

    static final long OTHER_MEMBER_RESERVATION_ID = 503L;

    static final long ALREADY_CANCELLED_RESERVATION_ID = 504L;

    static final long CANCELLATION_CLOSED_RESERVATION_ID = 507L;

    static final long PAYMENT_IN_PROGRESS_RESERVATION_ID = 508L;

    static final long MISSING_RESERVATION_ID = 999L;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private static final OffsetDateTime RESERVED_AT =
            OffsetDateTime.of(2026, 8, 1, 20, 7, 0, 0, KST);

    private static final OffsetDateTime CANCELLED_AT =
            OffsetDateTime.of(2026, 8, 1, 20, 15, 0, 0, KST);

    private static final OffsetDateTime PERFORMANCE_STARTS_AT =
            OffsetDateTime.of(2026, 9, 1, 19, 0, 0, 0, KST);

    private static final Map<Long, StubReservation> RESERVATIONS = createReservations();

    private StubReservations() {
    }

    static PageResponse<ReservationSummaryResponse> page(int page, int size) {
        List<StubReservation> own = ownReservations();

        List<ReservationSummaryResponse> content = own.stream()
                .skip((long) page * size)
                .limit(size)
                .map(StubReservations::toSummary)
                .toList();

        return new PageResponse<>(content, page, size, own.size(), totalPages(own.size(), size));
    }

    private static int totalPages(int totalElements, int size) {
        return (totalElements + size - 1) / size;
    }

    static Optional<ReservationDetailResponse> detail(long reservationId) {
        return Optional.ofNullable(RESERVATIONS.get(reservationId))
                .map(StubReservations::toDetail);
    }

    static boolean exists(long reservationId) {
        return RESERVATIONS.containsKey(reservationId);
    }

    static boolean ownedByStubMember(long reservationId) {
        return Optional.ofNullable(RESERVATIONS.get(reservationId))
                .map(StubReservation::owned)
                .orElse(false);
    }

    static boolean alreadyCancelled(long reservationId) {
        return Optional.ofNullable(RESERVATIONS.get(reservationId))
                .map(reservation -> reservation.status() == ReservationStatus.CANCELLED)
                .orElse(false);
    }

    static boolean cancellationClosed(long reservationId) {
        return reservationId == CANCELLATION_CLOSED_RESERVATION_ID;
    }

    static boolean paymentInProgress(long reservationId) {
        return reservationId == PAYMENT_IN_PROGRESS_RESERVATION_ID;
    }

    static ReservationCancelResponse cancel(long reservationId) {
        return new ReservationCancelResponse(reservationId, ReservationStatus.CANCELLED, CANCELLED_AT);
    }

    private static List<StubReservation> ownReservations() {
        return RESERVATIONS.values().stream()
                .filter(StubReservation::owned)
                .sorted(Comparator.comparingLong(StubReservation::id).reversed())
                .toList();
    }

    private static String orderIdOf(long reservationId) {
        return "reservation-" + reservationId + "-1";
    }

    private static ReservationSummaryResponse toSummary(StubReservation reservation) {
        return new ReservationSummaryResponse(
                reservation.id(),
                reservation.concertTitle(),
                reservation.posterUrl(),
                PERFORMANCE_STARTS_AT,
                reservation.venue(),
                reservation.seatIds().size(),
                reservation.totalAmount(),
                reservation.status());
    }

    private static ReservationDetailResponse toDetail(StubReservation reservation) {
        List<ReservationDetailResponse.Seat> seats = reservation.seatIds().stream()
                .map(seatId -> StubSeatMap.seatOf(reservation.scheduleId(), seatId))
                .flatMap(Optional::stream)
                .map(StubReservations::toDetailSeat)
                .toList();

        boolean paid = reservation.status() == ReservationStatus.CONFIRMED;

        return new ReservationDetailResponse(
                reservation.id(),
                reservation.status(),
                new ReservationDetailResponse.Concert(
                        reservation.concertId(), reservation.concertTitle(), reservation.posterUrl()),
                new ReservationDetailResponse.Schedule(
                        reservation.scheduleId(), PERFORMANCE_STARTS_AT, reservation.venue()),
                seats,
                reservation.totalAmount(),
                paid ? "payment-key-" + reservation.id() : null,
                paid ? orderIdOf(reservation.id()) : null,
                RESERVED_AT);
    }

    private static ReservationDetailResponse.Seat toDetailSeat(SeatMapResponse.Seat seat) {
        return new ReservationDetailResponse.Seat(
                seat.id(), seat.section(), seat.row(), seat.number(), seat.grade(), seat.price());
    }

    private static Map<Long, StubReservation> createReservations() {
        Map<Long, StubReservation> reservations = new LinkedHashMap<>();
        reservations.put(PENDING_RESERVATION_ID, createReservation(
                PENDING_RESERVATION_ID, ReservationStatus.PENDING_PAYMENT, true, List.of(2011L, 2014L)));
        reservations.put(CONFIRMED_RESERVATION_ID, createReservation(
                CONFIRMED_RESERVATION_ID, ReservationStatus.CONFIRMED, true, List.of(2015L)));
        reservations.put(OTHER_MEMBER_RESERVATION_ID, createReservation(
                OTHER_MEMBER_RESERVATION_ID, ReservationStatus.CONFIRMED, false, List.of(2016L)));
        reservations.put(ALREADY_CANCELLED_RESERVATION_ID, createReservation(
                ALREADY_CANCELLED_RESERVATION_ID, ReservationStatus.CANCELLED, true, List.of(2011L)));
        reservations.put(CANCELLATION_CLOSED_RESERVATION_ID, createReservation(
                CANCELLATION_CLOSED_RESERVATION_ID, ReservationStatus.CONFIRMED, true, List.of(2012L)));
        reservations.put(PAYMENT_IN_PROGRESS_RESERVATION_ID, createReservation(
                PAYMENT_IN_PROGRESS_RESERVATION_ID, ReservationStatus.PENDING_PAYMENT, true, List.of(2013L)));
        return Collections.unmodifiableMap(reservations);
    }

    private static StubReservation createReservation(
            long id, ReservationStatus status, boolean owned, List<Long> seatIds) {

        long scheduleId = StubSchedules.OPEN_SCHEDULE_ID;
        long concertId = scheduleId / 100;
        long totalAmount = seatIds.stream()
                .map(seatId -> StubSeatMap.seatOf(scheduleId, seatId))
                .flatMap(Optional::stream)
                .mapToLong(SeatMapResponse.Seat::price)
                .sum();

        return new StubReservation(
                id,
                status,
                owned,
                concertId,
                "스텁 콘서트 " + concertId,
                "https://cdn.encore-ticket.test/posters/" + concertId + ".jpg",
                scheduleId,
                "스텁 공연장 " + concertId,
                seatIds,
                totalAmount,
                "스텁 콘서트 " + concertId + " VIP석 외 " + (seatIds.size() - 1) + "매");
    }

    private record StubReservation(
            long id,
            ReservationStatus status,
            boolean owned,
            long concertId,
            String concertTitle,
            String posterUrl,
            long scheduleId,
            String venue,
            List<Long> seatIds,
            long totalAmount,
            String orderName) {
    }
}
