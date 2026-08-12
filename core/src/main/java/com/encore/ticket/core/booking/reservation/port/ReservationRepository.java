package com.encore.ticket.core.booking.reservation.port;

import java.util.List;
import java.util.Optional;
import com.encore.ticket.core.booking.reservation.domain.Reservation;

public interface ReservationRepository {
    public Reservation findById(Long reservationId);

    public Optional<Reservation> findByHoldId(String holdId);

    public List<Reservation> findPageByMemberId(Long memberId, int page, int size);

    public long countByMemberId(Long memberId);

    public Reservation save(Reservation reservation);
}
