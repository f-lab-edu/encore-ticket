package com.encore.ticket.core.booking.hold.application;

import com.encore.ticket.core.booking.dto.SeatHoldResponse;
import com.encore.ticket.core.booking.exception.PurchaseLimitExceededException;
import com.encore.ticket.core.booking.exception.SeatAlreadyHeldException;
import com.encore.ticket.core.catalog.port.SeatCatalogReader;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.encore.ticket.core.booking.hold.domain.SeatHold;
import com.encore.ticket.core.booking.hold.port.SeatHoldRepository;

public class SeatHoldService {

    private static final int PURCHASE_LIMIT_PER_SCHEDULE = 4;

    private final SeatHoldRepository seatHoldRepository;
    private final SeatCatalogReader seatCatalogReader;
    private final Clock clock;

    public SeatHoldService(SeatHoldRepository seatHoldRepository, SeatCatalogReader seatCatalogReader, Clock clock) {
        this.seatHoldRepository = seatHoldRepository;
        this.seatCatalogReader = seatCatalogReader;
        this.clock = clock;
    }

    public SeatHoldResponse hold(long scheduleId, List<Long> seatIds, long memberId) {
        int alreadyHeld = seatHoldRepository.countActiveSeatsOf(scheduleId, memberId);
        if (alreadyHeld + seatIds.size() > PURCHASE_LIMIT_PER_SCHEDULE) {
            throw new PurchaseLimitExceededException();
        }

        Set<Long> occupied = seatHoldRepository.findOccupiedSeatIds(scheduleId);
        if (seatIds.stream().anyMatch(occupied::contains)) {
            throw new SeatAlreadyHeldException();
        }

        Map<Long, Long> prices = seatCatalogReader.pricesOf(seatIds);
        long totalAmount = seatIds.stream()
                .mapToLong(prices::get)
                .sum();

        SeatHold seatHold = SeatHold.hold(scheduleId, seatIds, memberId, clock);
        seatHoldRepository.save(seatHold);

        return new SeatHoldResponse(
                seatHold.holdId(), scheduleId, seatIds, totalAmount, seatHold.expiresAt());
    }
}
