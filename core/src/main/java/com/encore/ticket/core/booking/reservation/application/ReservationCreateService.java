package com.encore.ticket.core.booking.reservation.application;

import com.encore.ticket.core.booking.PaymentAttemptState;
import com.encore.ticket.core.booking.dto.ReservationCreateResponse;
import com.encore.ticket.core.booking.exception.HoldExpiredException;
import com.encore.ticket.core.booking.exception.HoldNotOwnedException;
import com.encore.ticket.core.booking.exception.ReservationCancelledException;
import com.encore.ticket.core.catalog.port.ScheduleCatalogReader;
import com.encore.ticket.core.catalog.domain.ScheduleInfo;
import com.encore.ticket.core.catalog.port.SeatCatalogReader;
import com.encore.ticket.core.catalog.domain.SeatInfo;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import com.encore.ticket.core.booking.reservation.domain.HeldSeats;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.HoldReader;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;

public class ReservationCreateService {

    private final ReservationRepository reservationRepository;
    private final HoldReader holdReader;
    private final SeatCatalogReader seatCatalogReader;
    private final ScheduleCatalogReader scheduleCatalogReader;
    private final Clock clock;

    public ReservationCreateService(ReservationRepository reservationRepository, HoldReader holdReader,
                             SeatCatalogReader seatCatalogReader, ScheduleCatalogReader scheduleCatalogReader,
                             Clock clock) {
        this.reservationRepository = reservationRepository;
        this.holdReader = holdReader;
        this.seatCatalogReader = seatCatalogReader;
        this.scheduleCatalogReader = scheduleCatalogReader;
        this.clock = clock;
    }

    public CreateResult create(String holdId, Long memberId, PaymentAttemptState lastAttempt) {
        HeldSeats hold = holdReader.findByHoldId(holdId);
        if (!hold.isOwnedBy(memberId)) {
            throw new HoldNotOwnedException();
        }

        Optional<Reservation> found = reservationRepository.findByHoldId(holdId);
        if (found.isEmpty()) {
            if (hold.isExpired(clock)) {
                throw new HoldExpiredException();
            }
            return new CreateResult(issue(hold), true);
        }

        Reservation reservation = found.get();
        if (reservation.isCancelled()) {
            throw new ReservationCancelledException();
        }
        if (reservation.isExpired(clock)) {
            throw new HoldExpiredException();
        }
        if (reservation.isPendingPayment() && lastAttempt == PaymentAttemptState.FAILED) {
            reservation.startNextPaymentAttempt();
            reservationRepository.save(reservation);
        }

        List<SeatInfo> seats = seatCatalogReader.seatsByIds(hold.seatIds());
        ScheduleInfo schedule = scheduleCatalogReader.scheduleOf(hold.scheduleId());
        return new CreateResult(toResponse(reservation, schedule, seats), false);
    }

    private ReservationCreateResponse issue(HeldSeats hold) {
        List<SeatInfo> seats = seatCatalogReader.seatsByIds(hold.seatIds());
        ScheduleInfo schedule = scheduleCatalogReader.scheduleOf(hold.scheduleId());
        long amount = seats.stream().mapToLong(SeatInfo::price).sum();

        Reservation saved = reservationRepository.save(
                Reservation.create(hold, amount, schedule.startsAt(), clock));

        return toResponse(saved, schedule, seats);
    }

    private ReservationCreateResponse toResponse(Reservation reservation, ScheduleInfo schedule, List<SeatInfo> seats) {
        return new ReservationCreateResponse(
                reservation.id(),
                reservation.currentOrderId(),
                orderName(schedule.concertTitle(), seats),
                reservation.amount(),
                reservation.status(),
                reservation.expiresAt(),
                reservation.originalExpiresAt());
    }

    private String orderName(String concertTitle, List<SeatInfo> seats) {
        String grade = seats.getFirst().grade();
        if (seats.size() == 1) {
            return "%s %s석".formatted(concertTitle, grade);
        }
        return "%s %s석 외 %d매".formatted(concertTitle, grade, seats.size() - 1);
    }
}
