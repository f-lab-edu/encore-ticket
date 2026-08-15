package com.encore.ticket.storage.db.booking.reservation;

import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ReservationRepositoryImpl implements ReservationRepository {

    private final ReservationJpaRepository reservationJpa;
    private final ReservationSeatJpaRepository seatJpa;

    @Override
    public Optional<Reservation> findById(Long reservationId) {
        return reservationJpa.findById(reservationId).map(this::toDomain);
    }

    @Override
    public Optional<Reservation> findByHoldId(String holdId) {
        return reservationJpa.findByHoldId(holdId).map(this::toDomain);
    }

    @Override
    public List<Reservation> findPageByMemberId(Long memberId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        List<ReservationEntity> entities = reservationJpa.findByMemberId(memberId, pageRequest);

        if (entities.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Long>> seatIdsByReservation = seatIdsOf(
                entities.stream().map(ReservationEntity::id).toList());

        return entities.stream()
                .map(entity -> ReservationMapper.toDomain(entity, seatIdsByReservation.getOrDefault(entity.id(), List.of())))
                .toList();
    }

    @Override
    public long countByMemberId(Long memberId) {
        return reservationJpa.countByMemberId(memberId);
    }

    @Override
    @Transactional
    public Reservation save(Reservation reservation) {
        boolean isNew = reservation.id() == null;

        ReservationEntity saved = reservationJpa.saveAndFlush(ReservationMapper.toEntity(reservation));

        if (isNew) {
            seatJpa.saveAll(ReservationMapper.toSeatEntities(reservation, saved.id()));
        }

        return toDomain(saved);
    }

    private Reservation toDomain(ReservationEntity entity) {
        return ReservationMapper.toDomain(entity, seatJpa.findByReservationId(entity.id()).stream()
                .map(ReservationSeatEntity::seatId)
                .toList());
    }

    private Map<Long, List<Long>> seatIdsOf(List<Long> reservationIds) {
        return seatJpa.findByReservationIdIn(reservationIds).stream()
                .collect(Collectors.groupingBy(
                        ReservationSeatEntity::reservationId,
                        Collectors.mapping(ReservationSeatEntity::seatId, Collectors.toList())));
    }

}
