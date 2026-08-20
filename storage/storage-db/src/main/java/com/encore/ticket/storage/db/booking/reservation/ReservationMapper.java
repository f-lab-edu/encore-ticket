package com.encore.ticket.storage.db.booking.reservation;

import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.storage.db.booking.seat.SeatAssignmentEntity;

import java.util.List;

final class ReservationMapper {

    private ReservationMapper() {
    }

    static Reservation toDomain(ReservationEntity entity, List<Long> seatIds) {
        return Reservation.builder()
                .id(entity.id())
                .version(entity.version())
                .holdId(entity.holdId())
                .memberId(entity.memberId())
                .scheduleId(entity.scheduleId())
                .seatIds(seatIds)
                .amount(entity.amount())
                .status(entity.status())
                .reservedAt(entity.reservedAt())
                .performanceStartsAt(entity.performanceStartsAt())
                .originalExpiresAt(entity.originalExpiresAt())
                .expiresAt(entity.expiresAt())
                .paymentAttemptNo(entity.paymentAttemptNo())
                .paymentStartsAt(entity.paymentStartsAt())
                .cancelledAt(entity.cancelledAt())
                .build();
    }

    static ReservationEntity toEntity(Reservation reservation) {
        return ReservationEntity.builder()
                .id(reservation.id())
                .version(reservation.version())
                .holdId(reservation.holdId())
                .memberId(reservation.memberId())
                .scheduleId(reservation.scheduleId())
                .amount(reservation.amount())
                .status(reservation.status())
                .reservedAt(reservation.reservedAt())
                .performanceStartsAt(reservation.performanceStartsAt())
                .originalExpiresAt(reservation.originalExpiresAt())
                .expiresAt(reservation.expiresAt())
                .paymentAttemptNo(reservation.paymentAttemptNo())
                .paymentStartsAt(reservation.paymentStartsAt())
                .cancelledAt(reservation.cancelledAt())
                .build();
    }

    static List<ReservationSeatEntity> toSeatEntities(Reservation reservation, Long reservationId) {
        return reservation.seatIds().stream()
                .map(seatId -> ReservationSeatEntity.builder()
                        .reservationId(reservationId)
                        .seatId(seatId)
                        .scheduleId(reservation.scheduleId())
                        .build())
                .toList();
    }

    static List<SeatAssignmentEntity> toSeatAssignmentEntities(Reservation reservation, Long reservationId) {
        return reservation.seatIds().stream()
                .map(seatId -> SeatAssignmentEntity.builder()
                        .seatId(seatId)
                        .reservationId(reservationId)
                        .scheduleId(reservation.scheduleId())
                        .build())
                .toList();
    }
}
