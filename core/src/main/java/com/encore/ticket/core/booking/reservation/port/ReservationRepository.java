package com.encore.ticket.core.booking.reservation.port;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;

import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.exception.NotFoundException;

public interface ReservationRepository {

    Optional<Reservation> findById(Long reservationId);

    default Reservation getById(Long reservationId) {
        return findById(reservationId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 예매입니다: " + reservationId));
    }

    Optional<Reservation> findByHoldId(String holdId);

    List<Reservation> findPageByMemberId(Long memberId, int page, int size);

    long countByMemberId(Long memberId);

    Reservation save(Reservation reservation);

    Reservation saveIssued(Reservation reservation);

    /**
     * 예매를 잠근 뒤 소유자와 유효 상태를 재검증하고, 현재 주문의 결제가 FAILED일 때만
     * 다음 주문번호를 발급한다. 이미 발급된 미사용 주문번호는 그대로 반환한다.
     */
    Reservation prepareNextPaymentAttempt(String holdId, Long memberId);

    Reservation saveCancelled(Reservation cancelled);

    int expireBatch(OffsetDateTime now, int batchSize);
}
