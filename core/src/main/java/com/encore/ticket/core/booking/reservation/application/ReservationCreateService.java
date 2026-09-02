package com.encore.ticket.core.booking.reservation.application;

import com.encore.ticket.core.booking.PaymentAttemptState;
import com.encore.ticket.core.booking.dto.ReservationCreateResponse;
import com.encore.ticket.core.booking.exception.HoldExpiredException;
import com.encore.ticket.core.booking.exception.HoldNotOwnedException;
import com.encore.ticket.core.booking.exception.ReservationAlreadyExistsException;
import com.encore.ticket.core.catalog.port.ScheduleCatalogReader;
import com.encore.ticket.core.catalog.domain.ScheduleInfo;
import com.encore.ticket.core.catalog.port.SeatCatalogReader;
import com.encore.ticket.core.catalog.domain.SeatInfo;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.encore.ticket.core.booking.reservation.domain.HeldSeats;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.HoldReader;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationCreateService {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final ReservationRepository reservationRepository;
    private final HoldReader holdReader;
    private final SeatCatalogReader seatCatalogReader;
    private final ScheduleCatalogReader scheduleCatalogReader;
    private final Clock clock;

    public CreateResult create(String holdId, Long memberId, PaymentAttemptState lastAttempt) {
        return reservationRepository.findByHoldId(holdId)
                .map(reservation -> new CreateResult(resume(reservation, memberId, lastAttempt), false))
                .orElseGet(() -> createFresh(holdId, memberId, lastAttempt));
    }

    private CreateResult createFresh(String holdId, Long memberId, PaymentAttemptState lastAttempt) {
        HeldSeats hold = holdReader.getByHoldId(holdId);
        if (!hold.isOwnedBy(memberId)) {
            throw new HoldNotOwnedException();
        }

        try {
            return new CreateResult(issueFresh(hold), true);
        } catch (ReservationAlreadyExistsException conflict) {
            // saveIssued의 트랜잭션이 롤백된 뒤, 먼저 커밋된 예매를 새로 읽는다.
            Reservation existing = reservationRepository.findByHoldId(holdId).orElseThrow(() -> conflict);
            return new CreateResult(resume(existing, memberId, lastAttempt), false);
        }
    }

    private ReservationCreateResponse resume(Reservation stored, Long memberId,
                                             PaymentAttemptState lastAttempt) {
        stored.validatePaymentPreparation(memberId, clock);

        Reservation reservation = stored;
        if (stored.isPendingPayment() && lastAttempt == PaymentAttemptState.FAILED) {
            // app에서 조회한 상태는 힌트다. 변경 여부는 잠금 안에서 현재 주문으로 다시 판단한다.
            reservation = reservationRepository.prepareNextPaymentAttempt(stored.holdId(), memberId);
        }

        List<SeatInfo> seats = seatCatalogReader.seatsByIds(reservation.seatIds());
        ScheduleInfo schedule = scheduleCatalogReader.scheduleOf(reservation.scheduleId());
        return toResponse(reservation, schedule, seats);
    }

    private ReservationCreateResponse issueFresh(HeldSeats hold) {
        if (hold.isExpired(clock)) {
            throw new HoldExpiredException();
        }

        List<SeatInfo> seats = seatCatalogReader.seatsByIds(hold.seatIds());
        ScheduleInfo schedule = scheduleCatalogReader.scheduleOf(hold.scheduleId());
        long amount = seats.stream().mapToLong(SeatInfo::price).sum();

        Reservation saved = reservationRepository.saveIssued(
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
                reservation.expiresAt().withOffsetSameInstant(KST).truncatedTo(ChronoUnit.SECONDS),
                reservation.originalExpiresAt().withOffsetSameInstant(KST).truncatedTo(ChronoUnit.SECONDS));
    }

    private String orderName(String concertTitle, List<SeatInfo> seats) {
        String grade = seats.getFirst().grade();
        if (seats.size() == 1) {
            return "%s %s석".formatted(concertTitle, grade);
        }
        return "%s %s석 외 %d매".formatted(concertTitle, grade, seats.size() - 1);
    }
}
