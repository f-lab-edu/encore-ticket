package com.encore.ticket.payment.controller;

import com.encore.ticket.payment.api.dto.PaymentConfirmResponse;
import com.encore.ticket.payment.api.dto.PaymentResultResponse;
import com.encore.ticket.payment.api.exception.AmountMismatchException;
import com.encore.ticket.payment.api.exception.CancelledReservationException;
import com.encore.ticket.payment.api.exception.ExpiredReservationException;
import com.encore.ticket.payment.api.exception.OrderIdAlreadyBoundException;
import com.encore.ticket.payment.api.exception.PaymentKeyReusedException;
import com.encore.ticket.payment.api.exception.ReservationNotOwnedException;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    @PostMapping("/confirm")
    ResponseEntity<PaymentConfirmResponse> confirm(@Valid @RequestBody PaymentConfirmRequest request) {
        String orderId = request.orderId();

        if (!StubPayments.exists(orderId)) {
            throw notFound(orderId);
        }
        if (!StubPayments.ownedByStubMember(orderId)) {
            throw new ReservationNotOwnedException();
        }
        if ("CANCELLED".equals(StubPayments.reservationStatusOf(orderId))) {
            throw new CancelledReservationException();
        }
        if ("EXPIRED".equals(StubPayments.reservationStatusOf(orderId))) {
            throw new ExpiredReservationException();
        }
        if (!StubPayments.paymentKeyOf(orderId).equals(request.paymentKey())) {
            if (StubPayments.paymentKeyBoundToOtherOrder(request.paymentKey(), orderId)) {
                throw new PaymentKeyReusedException();
            }
            throw new OrderIdAlreadyBoundException();
        }
        if (request.amount() != StubPayments.EXPECTED_AMOUNT) {
            throw new AmountMismatchException();
        }

        HttpStatus status = StubPayments.settled(orderId) ? HttpStatus.OK : HttpStatus.ACCEPTED;

        return ResponseEntity.status(status).body(StubPayments.confirm(orderId));
    }

    @GetMapping("/{orderId}")
    PaymentResultResponse result(@PathVariable("orderId") String orderId) {
        if (!StubPayments.ownedByStubMember(orderId)) {
            if (StubPayments.exists(orderId)) {
                throw new ReservationNotOwnedException();
            }
            throw notFound(orderId);
        }

        return StubPayments.result(orderId).orElseThrow(() -> notFound(orderId));
    }

    private static ResponseStatusException notFound(String orderId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 주문입니다: " + orderId);
    }
}
