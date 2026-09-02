package com.encore.ticket.core.booking.hold.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.encore.ticket.core.booking.dto.SeatHoldResponse;
import com.encore.ticket.core.booking.dto.SeatHoldResult;
import com.encore.ticket.core.booking.exception.IdempotencyKeyReusedException;
import com.encore.ticket.core.booking.exception.PurchaseLimitExceededException;
import com.encore.ticket.core.booking.exception.SeatAlreadyHeldException;
import com.encore.ticket.core.booking.hold.domain.SeatHold;
import com.encore.ticket.core.booking.hold.port.SeatHoldAcquireResult;
import com.encore.ticket.core.booking.hold.port.SeatHoldAcquisition;
import com.encore.ticket.core.booking.hold.port.SeatHoldRepository;
import com.encore.ticket.core.catalog.domain.SeatInfo;
import com.encore.ticket.core.catalog.port.SeatCatalogReader;
import com.encore.ticket.core.exception.InvalidRequestFieldException;
import com.encore.ticket.core.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SeatHoldService {

    private static final int PURCHASE_LIMIT_PER_SCHEDULE = 4;
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final String SEAT_IDS_FIELD = "seatIds";

    private final SeatHoldRepository seatHoldRepository;
    private final SeatCatalogReader seatCatalogReader;
    private final Clock clock;

    public SeatHoldResult hold(
            long scheduleId, List<Long> seatIds, long memberId, String idempotencyKey) {

        List<SeatInfo> seats = verifiedSeatsOf(scheduleId, seatIds);
        long totalAmount = seats.stream().mapToLong(SeatInfo::price).sum();

        SeatHold seatHold = SeatHold.hold(scheduleId, seatIds, memberId, clock);
        SeatHoldAcquisition acquisition = seatHoldRepository.acquire(
                seatHold,
                PURCHASE_LIMIT_PER_SCHEDULE,
                idempotencyKey,
                fingerprintOf(scheduleId, seatIds));

        switch (acquisition.result()) {
            case SEAT_ALREADY_HELD -> throw new SeatAlreadyHeldException();
            case PURCHASE_LIMIT_EXCEEDED -> throw new PurchaseLimitExceededException();
            case IDEMPOTENCY_KEY_REUSED -> throw new IdempotencyKeyReusedException();
            case ACQUIRED, REPLAYED -> {
            }
        }

        SeatHoldResponse response = new SeatHoldResponse(
                acquisition.holdId(),
                scheduleId,
                seatIds,
                totalAmount,
                displayed(acquisition.expiresAt()));

        return new SeatHoldResult(
                response, acquisition.result() == SeatHoldAcquireResult.REPLAYED);
    }

    private static OffsetDateTime displayed(OffsetDateTime expiresAt) {
        return expiresAt.withOffsetSameInstant(KST).truncatedTo(ChronoUnit.SECONDS);
    }

    private List<SeatInfo> verifiedSeatsOf(long scheduleId, List<Long> seatIds) {
        Map<Long, SeatInfo> found = seatCatalogReader.seatsByIds(seatIds).stream()
                .collect(Collectors.toMap(SeatInfo::id, Function.identity()));

        return seatIds.stream()
                .map(seatId -> verified(scheduleId, seatId, found.get(seatId)))
                .toList();
    }

    private static SeatInfo verified(long scheduleId, long seatId, SeatInfo seat) {
        if (seat == null) {
            throw new NotFoundException("존재하지 않는 좌석입니다: " + seatId);
        }
        if (seat.scheduleId() != scheduleId) {
            throw new InvalidRequestFieldException(
                    SEAT_IDS_FIELD, "회차에 속하지 않는 좌석입니다: " + seatId);
        }
        return seat;
    }

    private static String fingerprintOf(long scheduleId, List<Long> seatIds) {
        return scheduleId + ":" + seatIds.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
