package com.encore.ticket.payment.controller;

import com.encore.ticket.core.payment.application.PaymentService;
import com.encore.ticket.core.payment.dto.PaymentConfirmResponse;
import com.encore.ticket.core.payment.dto.PaymentRefundStatus;
import com.encore.ticket.core.payment.dto.PaymentResultResponse;
import com.encore.ticket.core.payment.dto.PaymentStatus;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/confirm")
    ResponseEntity<PaymentConfirmResponse> confirm(
            @Valid @RequestBody PaymentConfirmRequest request,
            @AuthenticationPrincipal Long memberId) {
        PaymentConfirmResponse response = paymentService.confirm(
                request.paymentKey(), request.orderId(), request.amount(), memberId);
        return ResponseEntity.status(statusOf(response)).body(response);
    }

    @GetMapping("/{orderId}")
    ResponseEntity<PaymentResultResponse> result(
            @PathVariable("orderId") String orderId,
            @AuthenticationPrincipal Long memberId) {
        PaymentResultResponse response = paymentService.result(orderId, memberId);
        HttpStatus status = response.refundStatus() == PaymentRefundStatus.PENDING
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    private static HttpStatus statusOf(PaymentConfirmResponse response) {
        if (response.paymentStatus() == PaymentStatus.PENDING
                || response.refundStatus() == PaymentRefundStatus.PENDING) {
            return HttpStatus.ACCEPTED;
        }
        return HttpStatus.OK;
    }
}
