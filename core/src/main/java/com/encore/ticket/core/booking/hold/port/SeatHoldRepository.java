package com.encore.ticket.core.booking.hold.port;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import com.encore.ticket.core.booking.hold.domain.SeatHold;

public interface SeatHoldRepository {

    Map<Long, OffsetDateTime> holdExpiryBySeatId(Long scheduleId);

    // 저장된 멱등 기록이 있으면 재응답 또는 키 재사용 결과를 반환한다. 새 선점은 생성하지 않는다.
    Optional<SeatHoldAcquisition> findPreviousAcquisition(
            long scheduleId, long memberId, String idempotencyKey, String requestFingerprint);

    SeatHoldAcquisition acquire(
            SeatHold seatHold,
            int maxSeatsPerSchedule,
            String idempotencyKey,
            String requestFingerprint);
}
