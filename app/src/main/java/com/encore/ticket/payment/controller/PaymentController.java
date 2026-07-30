package com.encore.ticket.payment.controller;

import com.encore.ticket.payment.api.dto.PaymentConfirmResponse;
import com.encore.ticket.payment.api.dto.PaymentResultResponse;
import com.encore.ticket.payment.api.exception.CancelledReservationException;
import com.encore.ticket.payment.api.exception.ExpiredReservationException;

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
            throw forbidden();
        }
        if ("CANCELLED".equals(StubPayments.reservationStatusOf(orderId))) {
            throw new CancelledReservationException();
        }
        if ("EXPIRED".equals(StubPayments.reservationStatusOf(orderId))) {
            throw new ExpiredReservationException();
        }
        if (request.amount() != StubPayments.EXPECTED_AMOUNT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "요청 금액이 예매 금액과 다릅니다: " + request.amount());
        }

        PaymentConfirmResponse response = StubPayments.confirm(request.paymentKey(), orderId);
        HttpStatus status = StubPayments.alreadyCompleted(orderId) ? HttpStatus.OK : HttpStatus.ACCEPTED;

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{orderId}")
    PaymentResultResponse result(@PathVariable("orderId") String orderId) {
        if (!StubPayments.exists(orderId)) {
            throw notFound(orderId);
        }
        if (!StubPayments.ownedByStubMember(orderId)) {
            throw forbidden();
        }

        return StubPayments.result(orderId).orElseThrow(() -> notFound(orderId));
    }

    private static ResponseStatusException notFound(String orderId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 주문입니다: " + orderId);
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 사용자의 결제입니다.");
    }
}
