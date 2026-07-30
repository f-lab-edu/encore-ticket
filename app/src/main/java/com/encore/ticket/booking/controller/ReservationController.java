package com.encore.ticket.booking.controller;

import com.encore.ticket.booking.api.dto.ReservationCancelResponse;
import com.encore.ticket.booking.api.dto.ReservationCreateResponse;
import com.encore.ticket.booking.api.dto.ReservationDetailResponse;
import com.encore.ticket.booking.api.dto.ReservationSummaryResponse;
import com.encore.ticket.booking.api.dto.SeatHoldResponse;
import com.encore.ticket.booking.api.exception.HoldExpiredException;
import com.encore.ticket.booking.api.exception.HoldNotOwnedException;
import com.encore.ticket.booking.api.exception.IdempotencyKeyReusedException;
import com.encore.ticket.booking.api.exception.PurchaseLimitExceededException;
import com.encore.ticket.booking.api.exception.QueueNotAdmittedException;
import com.encore.ticket.booking.api.exception.ReservationNotOwnedException;
import com.encore.ticket.booking.api.exception.ReservationCancelledException;
import com.encore.ticket.booking.api.exception.SeatAlreadyHeldException;
import com.encore.ticket.catalog.api.dto.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/reservations")
public class ReservationController {

    @PostMapping("/holds")
    ResponseEntity<SeatHoldResponse> hold(
            @RequestHeader("X-Queue-Token") String queueToken,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SeatHoldRequest request) {

        if (!StubSchedules.exists(request.scheduleId())) {
            throw notFound("존재하지 않는 회차입니다: " + request.scheduleId());
        }
        if (!StubQueue.admitted(queueToken)) {
            throw new QueueNotAdmittedException();
        }
        if (StubReservations.REUSED_IDEMPOTENCY_KEY.equals(idempotencyKey)) {
            throw new IdempotencyKeyReusedException();
        }
        if (request.scheduleId() == StubReservations.PURCHASE_LIMIT_SCHEDULE_ID) {
            throw new PurchaseLimitExceededException();
        }

        for (long seatId : request.seatIds()) {
            if (!StubReservations.seatExists(seatId)) {
                throw notFound("존재하지 않는 좌석입니다: " + seatId);
            }
            if (!StubReservations.seatBelongsTo(request.scheduleId(), seatId)) {
                throw badRequest("회차에 속하지 않는 좌석입니다: " + seatId);
            }
            if (StubReservations.seatTaken(request.scheduleId(), seatId)) {
                throw new SeatAlreadyHeldException();
            }
        }

        SeatHoldResponse response = StubReservations.hold(request.scheduleId(), request.seatIds());
        HttpStatus status = StubReservations.REPLAYED_IDEMPOTENCY_KEY.equals(idempotencyKey)
                ? HttpStatus.OK
                : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(response);
    }

    @PostMapping
    ResponseEntity<ReservationCreateResponse> create(@Valid @RequestBody ReservationCreateRequest request) {
        String holdId = request.holdId();

        if (!StubReservations.holdExists(holdId)) {
            throw notFound("존재하지 않는 선점 정보입니다: " + holdId);
        }
        if (StubReservations.OTHER_MEMBER_HOLD_ID.equals(holdId)) {
            throw new HoldNotOwnedException();
        }
        if (StubReservations.CANCELLED_HOLD_ID.equals(holdId)) {
            throw new ReservationCancelledException();
        }
        if (StubReservations.EXPIRED_HOLD_ID.equals(holdId)) {
            throw new HoldExpiredException();
        }

        ReservationCreateResponse response = StubReservations.create(holdId);
        HttpStatus status = StubReservations.createdBefore(holdId) ? HttpStatus.OK : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping
    PageResponse<ReservationSummaryResponse> reservations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {

        return StubReservations.page(page, size);
    }

    @GetMapping("/{reservationId}")
    ReservationDetailResponse reservation(@PathVariable("reservationId") long reservationId) {
        if (!StubReservations.exists(reservationId)) {
            throw reservationNotFound(reservationId);
        }
        if (!StubReservations.ownedByStubMember(reservationId)) {
            throw new ReservationNotOwnedException();
        }

        return StubReservations.detail(reservationId)
                .orElseThrow(() -> reservationNotFound(reservationId));
    }

    @PatchMapping("/{reservationId}")
    ReservationCancelResponse cancel(
            @PathVariable("reservationId") long reservationId,
            @Valid @RequestBody ReservationCancelRequest request) {

        if (!StubReservations.exists(reservationId)) {
            throw reservationNotFound(reservationId);
        }
        if (!StubReservations.ownedByStubMember(reservationId)) {
            throw new ReservationNotOwnedException();
        }

        return StubReservations.cancel(reservationId);
    }

    private static ResponseStatusException reservationNotFound(long reservationId) {
        return notFound("존재하지 않는 예매입니다: " + reservationId);
    }

    private static ResponseStatusException notFound(String detail) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, detail);
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
