package com.encore.ticket.core.booking.hold.application;

import com.encore.ticket.core.booking.dto.SeatHoldResponse;
import com.encore.ticket.core.booking.exception.PurchaseLimitExceededException;
import com.encore.ticket.core.booking.exception.SeatAlreadyHeldException;
import com.encore.ticket.core.catalog.port.SeatCatalogReader;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.encore.ticket.core.booking.hold.domain.SeatHold;
import com.encore.ticket.core.booking.hold.port.SeatHoldAcquireResult;
import com.encore.ticket.core.booking.hold.port.SeatHoldRepository;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SeatHoldService {

    private static final int PURCHASE_LIMIT_PER_SCHEDULE = 4;

    private final SeatHoldRepository seatHoldRepository;
    private final SeatCatalogReader seatCatalogReader;
    private final Clock clock;

    public SeatHoldResponse hold(long scheduleId, List<Long> seatIds, long memberId) {
        Map<Long, Long> prices = seatCatalogReader.pricesOf(seatIds);
        long totalAmount = seatIds.stream()
                .mapToLong(prices::get)
                .sum();

        SeatHold seatHold = SeatHold.hold(scheduleId, seatIds, memberId, clock);
        SeatHoldAcquireResult result = seatHoldRepository.acquire(
                seatHold, PURCHASE_LIMIT_PER_SCHEDULE);
        switch (result) {
            case SEAT_ALREADY_HELD -> throw new SeatAlreadyHeldException();
            case PURCHASE_LIMIT_EXCEEDED -> throw new PurchaseLimitExceededException();
            case ACQUIRED -> {
            }
        }

        return new SeatHoldResponse(
                seatHold.holdId(), scheduleId, seatIds, totalAmount, seatHold.expiresAt());
    }
}
