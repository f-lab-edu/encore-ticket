package com.encore.ticket.booking.internal.hold;

import com.encore.ticket.booking.api.dto.SeatHoldResponse;
import com.encore.ticket.booking.api.exception.PurchaseLimitExceededException;
import com.encore.ticket.booking.api.exception.SeatAlreadyHeldException;
import com.encore.ticket.catalog.api.SeatCatalogReader;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class SeatHoldService {

    private static final int HOLD_MINUTES = 7;
    private static final int PURCHASE_LIMIT_PER_SCHEDULE = 4;

    private final SeatHoldRepository seatHoldRepository;
    private final SeatCatalogReader seatCatalogReader;
    private final Clock clock;

    SeatHoldService(SeatHoldRepository seatHoldRepository, SeatCatalogReader seatCatalogReader, Clock clock) {
        this.seatHoldRepository = seatHoldRepository;
        this.seatCatalogReader = seatCatalogReader;
        this.clock = clock;
    }

    SeatHoldResponse hold(long scheduleId, List<Long> seatIds, long memberId) {
        int alreadyHeld = seatHoldRepository.countActiveSeatsOf(scheduleId, memberId);
        if (alreadyHeld + seatIds.size() > PURCHASE_LIMIT_PER_SCHEDULE) {
            throw new PurchaseLimitExceededException();
        }

        Set<Long> occupied = seatHoldRepository.findOccupiedSeatIds(scheduleId);
        if (seatIds.stream().anyMatch(occupied::contains)) {
            throw new SeatAlreadyHeldException();
        }

        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plusMinutes(HOLD_MINUTES);

        Map<Long, Long> prices = seatCatalogReader.pricesOf(seatIds);
        long totalAmount = seatIds.stream()
                .mapToLong(prices::get)
                .sum();

        SeatHold seatHold = new SeatHold(
                UUID.randomUUID().toString(), scheduleId, seatIds, memberId, expiresAt);
        seatHoldRepository.save(seatHold);

        return new SeatHoldResponse(
                seatHold.holdId(), scheduleId, seatIds, totalAmount, expiresAt);
    }
}
