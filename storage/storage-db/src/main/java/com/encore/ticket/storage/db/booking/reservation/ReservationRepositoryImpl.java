package com.encore.ticket.storage.db.booking.reservation;

import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;
import com.encore.ticket.core.booking.exception.ReservationAlreadyExistsException;
import com.encore.ticket.core.booking.exception.ReservationConcurrentModificationException;
import com.encore.ticket.core.booking.exception.SeatAlreadyHeldException;
import com.encore.ticket.storage.db.booking.seat.SeatAssignmentJpaRepository;
import com.encore.ticket.storage.db.payment.PaymentJpaRepository;
import com.encore.ticket.core.payment.dto.PaymentStatus;
import com.encore.ticket.core.exception.NotFoundException;
import java.time.Clock;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.hibernate.exception.ConstraintViolationException;

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
    private final SeatAssignmentJpaRepository seatAssignmentJpa;
    private final PaymentJpaRepository paymentJpa;
    private final Clock clock;

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

    @Override
    @Transactional
    public Reservation saveIssued(Reservation reservation) {
        if (reservation.id() != null) {
            throw new IllegalArgumentException("이미 저장된 예매는 발급할 수 없습니다: " + reservation.id());
        }

        ReservationEntity saved;
        try {
            saved = reservationJpa.saveAndFlush(ReservationMapper.toEntity(reservation));
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateConstraint(exception, "reservation.uk_reservation_hold")) {
                throw new ReservationAlreadyExistsException(exception);
            }
            throw exception;
        }

        seatJpa.saveAll(ReservationMapper.toSeatEntities(reservation, saved.id()));
        try {
            seatAssignmentJpa.saveAllAndFlush(ReservationMapper.toSeatAssignmentEntities(reservation, saved.id()));
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateConstraint(exception, "seat_assignment.PRIMARY")) {
                throw new SeatAlreadyHeldException();
            }
            throw exception;
        }

        return toDomain(saved);
    }

    @Override
    @Transactional
    public Reservation prepareNextPaymentAttempt(String holdId, Long memberId) {
        ReservationEntity locked = reservationJpa.findByHoldIdForUpdate(holdId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 예매입니다: " + holdId));
        Reservation current = toDomain(locked);
        current.validatePaymentPreparation(memberId, clock);

        if (!current.isPendingPayment()) {
            return current;
        }

        boolean failed = paymentJpa.findByOrderIdForUpdate(current.currentOrderId())
                .map(payment -> payment.status() == PaymentStatus.FAILED)
                .orElse(false);
        // 결제 행 잠금 대기 중 결제 마감 시각이 지났을 수 있으므로 최종 변경 직전에 다시 검증한다.
        current.validatePaymentPreparation(memberId, clock);
        if (!failed) {
            return current;
        }

        Reservation next = current.startNextPaymentAttempt();
        return toDomain(reservationJpa.saveAndFlush(ReservationMapper.toEntity(next)));
    }

    @Override
    @Transactional
    public Reservation saveCancelled(Reservation cancelled) {
        if (!cancelled.isCancelled()) {
            throw new IllegalArgumentException("취소 상태가 아닌 예매는 좌석을 해제할 수 없습니다: " + cancelled.id());
        }

        ReservationEntity saved;
        try {
            saved = reservationJpa.saveAndFlush(ReservationMapper.toEntity(cancelled));
        } catch (OptimisticLockingFailureException exception) {
            throw new ReservationConcurrentModificationException(exception);
        }

        seatAssignmentJpa.deleteByReservationId(saved.id());

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

    private static boolean isDuplicateConstraint(Throwable failure, String constraintName) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getSQLException().getErrorCode() == 1062
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

}
