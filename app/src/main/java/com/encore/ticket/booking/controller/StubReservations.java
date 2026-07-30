package com.encore.ticket.booking.controller;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.encore.ticket.booking.api.dto.ReservationCancelResponse;
import com.encore.ticket.booking.api.dto.ReservationCreateResponse;
import com.encore.ticket.booking.api.dto.ReservationDetailResponse;
import com.encore.ticket.booking.api.dto.ReservationStatus;
import com.encore.ticket.booking.api.dto.ReservationSummaryResponse;
import com.encore.ticket.booking.api.dto.SeatHoldResponse;
import com.encore.ticket.booking.api.dto.SeatMapResponse;
import com.encore.ticket.booking.api.dto.SeatStatus;
import com.encore.ticket.catalog.api.dto.PageResponse;

final class StubReservations {

    static final String NEW_IDEMPOTENCY_KEY = "idem-new";

    static final String REPLAYED_IDEMPOTENCY_KEY = "idem-replay";

    static final String REUSED_IDEMPOTENCY_KEY = "idem-conflict";

    static final String OWN_HOLD_ID = "hold_ok";

    static final String REPLAYED_HOLD_ID = "hold_replay";

    static final String OTHER_MEMBER_HOLD_ID = "hold_other";

    static final String CANCELLED_HOLD_ID = "hold_cancelled";

    static final String EXPIRED_HOLD_ID = "hold_expired";

    static final String MISSING_HOLD_ID = "hold_missing";

    static final long PURCHASE_LIMIT_SCHEDULE_ID = 401L;

    static final long PENDING_RESERVATION_ID = 501L;

    static final long CONFIRMED_RESERVATION_ID = 502L;

    static final long OTHER_MEMBER_RESERVATION_ID = 503L;

    static final long ALREADY_CANCELLED_RESERVATION_ID = 504L;

    static final long MISSING_RESERVATION_ID = 999L;

    static final int MAX_SEATS_PER_REQUEST = 4;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private static final OffsetDateTime HOLD_EXPIRES_AT =
            OffsetDateTime.of(2026, 8, 1, 20, 10, 0, 0, KST);

    private static final OffsetDateTime RESERVED_AT =
            OffsetDateTime.of(2026, 8, 1, 20, 7, 0, 0, KST);

    private static final OffsetDateTime CANCELLED_AT =
            OffsetDateTime.of(2026, 8, 1, 20, 15, 0, 0, KST);

    private static final OffsetDateTime PERFORMANCE_STARTS_AT =
            OffsetDateTime.of(2026, 9, 1, 19, 0, 0, 0, KST);

    private static final Set<String> KNOWN_HOLD_IDS = Set.of(
            OWN_HOLD_ID, REPLAYED_HOLD_ID, OTHER_MEMBER_HOLD_ID, CANCELLED_HOLD_ID, EXPIRED_HOLD_ID);

    private static final Map<Long, StubReservation> RESERVATIONS = createReservations();

    private StubReservations() {
    }

    static boolean seatExists(long seatId) {
        long scheduleId = seatId / 10;
        return StubSchedules.exists(scheduleId) && StubSeatMap.seatOf(scheduleId, seatId).isPresent();
    }

    static boolean seatBelongsTo(long scheduleId, long seatId) {
        return StubSeatMap.seatOf(scheduleId, seatId).isPresent();
    }

    static boolean seatTaken(long scheduleId, long seatId) {
        return StubSeatMap.seatOf(scheduleId, seatId)
                .map(seat -> seat.status() != SeatStatus.AVAILABLE)
                .orElse(false);
    }

    static SeatHoldResponse hold(long scheduleId, List<Long> seatIds) {
        long totalAmount = seatIds.stream()
                .map(seatId -> StubSeatMap.seatOf(scheduleId, seatId))
                .flatMap(Optional::stream)
                .mapToLong(SeatMapResponse.Seat::price)
                .sum();

        return new SeatHoldResponse(OWN_HOLD_ID, scheduleId, seatIds, totalAmount, HOLD_EXPIRES_AT);
    }

    static boolean holdExists(String holdId) {
        return KNOWN_HOLD_IDS.contains(holdId);
    }

    static ReservationCreateResponse create(String holdId) {
        long reservationId = REPLAYED_HOLD_ID.equals(holdId)
                ? CONFIRMED_RESERVATION_ID
                : PENDING_RESERVATION_ID;

        StubReservation reservation = RESERVATIONS.get(reservationId);

        return new ReservationCreateResponse(
                reservation.id(),
                orderIdOf(reservation.id()),
                reservation.orderName(),
                reservation.totalAmount(),
                reservation.status(),
                HOLD_EXPIRES_AT,
                HOLD_EXPIRES_AT);
    }

    static boolean createdBefore(String holdId) {
        return REPLAYED_HOLD_ID.equals(holdId);
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
