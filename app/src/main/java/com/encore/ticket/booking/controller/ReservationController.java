package com.encore.ticket.booking.controller;

import com.encore.ticket.core.booking.dto.ReservationCancelResponse;
import com.encore.ticket.core.booking.dto.ReservationCreateResponse;
import com.encore.ticket.core.booking.dto.ReservationDetailResponse;
import com.encore.ticket.core.booking.dto.ReservationSummaryResponse;
import com.encore.ticket.core.booking.dto.SeatHoldResponse;
import com.encore.ticket.core.booking.dto.SeatHoldResult;
import com.encore.ticket.core.booking.exception.CancellationClosedException;
import com.encore.ticket.core.booking.exception.HoldExpiredException;
import com.encore.ticket.core.booking.exception.HoldNotOwnedException;
import com.encore.ticket.core.booking.exception.PaymentInProgressException;
import com.encore.ticket.core.booking.exception.ReservationNotOwnedException;
import com.encore.ticket.core.booking.exception.ReservationCancelledException;
import com.encore.ticket.core.booking.hold.application.SeatHoldService;
import com.encore.ticket.core.catalog.dto.PageResponse;
import com.encore.ticket.core.booking.queue.application.QueueAuthorizationService;
import com.encore.ticket.core.catalog.port.ScheduleCatalogReader;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    private final QueueAuthorizationService queueAuthorizationService;
    private final SeatHoldService seatHoldService;
    private final ScheduleCatalogReader scheduleCatalogReader;

    public ReservationController(
            QueueAuthorizationService queueAuthorizationService,
            SeatHoldService seatHoldService,
            ScheduleCatalogReader scheduleCatalogReader) {
        this.queueAuthorizationService = queueAuthorizationService;
        this.seatHoldService = seatHoldService;
        this.scheduleCatalogReader = scheduleCatalogReader;
    }

    @PostMapping("/holds")
    ResponseEntity<SeatHoldResponse> hold(
            @RequestHeader("X-Queue-Token") String queueToken,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody SeatHoldRequest request) {

        scheduleCatalogReader.scheduleOf(request.scheduleId());
        queueAuthorizationService.authorize(request.scheduleId(), memberId, queueToken);

        SeatHoldResult result = seatHoldService.hold(
                request.scheduleId(), request.seatIds(), memberId, idempotencyKey);

        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(result.response());
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
    ResponseEntity<ReservationCancelResponse> cancel(
            @PathVariable("reservationId") long reservationId,
            @Valid @RequestBody ReservationCancelRequest request) {

        if (!StubReservations.exists(reservationId)) {
            throw reservationNotFound(reservationId);
        }
        if (!StubReservations.ownedByStubMember(reservationId)) {
            throw new ReservationNotOwnedException();
        }
        if (StubReservations.alreadyCancelled(reservationId)) {
            return ResponseEntity.noContent().build();
        }
        if (StubReservations.cancellationClosed(reservationId)) {
            throw new CancellationClosedException();
        }
        if (StubReservations.paymentInProgress(reservationId)) {
            throw new PaymentInProgressException();
        }

        return ResponseEntity.ok(StubReservations.cancel(reservationId));
    }

    private static ResponseStatusException reservationNotFound(long reservationId) {
        return notFound("존재하지 않는 예매입니다: " + reservationId);
    }

    private static ResponseStatusException notFound(String detail) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, detail);
    }
}
