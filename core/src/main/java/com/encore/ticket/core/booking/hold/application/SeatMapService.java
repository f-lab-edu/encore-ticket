package com.encore.ticket.core.booking.hold.application;

import com.encore.ticket.core.booking.dto.SeatMapResponse;
import com.encore.ticket.core.booking.dto.SeatStatus;
import com.encore.ticket.core.booking.seat.port.SeatAssignmentReader;
import com.encore.ticket.core.catalog.port.SeatCatalogReader;
import com.encore.ticket.core.catalog.domain.SeatInfo;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.encore.ticket.core.booking.hold.port.SeatHoldRepository;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SeatMapService {

    private final SeatHoldRepository seatHoldRepository;
    private final SeatAssignmentReader seatAssignmentReader;
    private final SeatCatalogReader  seatCatalogReader;
    private final Clock clock;

    public SeatMapResponse seatMap(Long scheduleId) {
        List<SeatInfo> seats = seatCatalogReader.seatsOf(scheduleId);
        Map<Long, OffsetDateTime> holdExpiry = seatHoldRepository.holdExpiryBySeatId(scheduleId);
        Set<Long> assigned = seatAssignmentReader.assignedSeatIdsOf(scheduleId);
        OffsetDateTime now = OffsetDateTime.now(clock);

        List<SeatMapResponse.Seat> result = seats.stream()
                .map(seat -> new SeatMapResponse.Seat(
                        seat.id(),
                        seat.section(), seat.row(), seat.number(), seat.grade(), seat.price(),
                        statusOf(seat.id(), holdExpiry, assigned, now)
                )).toList();

        return new SeatMapResponse(scheduleId, result);
    }

    private SeatStatus statusOf(Long seatId, Map<Long, OffsetDateTime> holdExpiry, Set<Long> assigned, OffsetDateTime now) {
        if (assigned.contains(seatId)) {
            return SeatStatus.RESERVED;
        }

        OffsetDateTime expiresAt = holdExpiry.get(seatId);
        if (expiresAt != null && expiresAt.isAfter(now)) {
            return SeatStatus.HELD;
        }

        return SeatStatus.AVAILABLE;
    }
}
