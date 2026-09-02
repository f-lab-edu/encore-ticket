package com.encore.ticket.core.booking.hold.port;

import java.time.OffsetDateTime;

public record SeatHoldAcquisition(
        SeatHoldAcquireResult result,
        String holdId,
        OffsetDateTime expiresAt) {

    public static SeatHoldAcquisition failed(SeatHoldAcquireResult result) {
        return new SeatHoldAcquisition(result, null, null);
    }
}
