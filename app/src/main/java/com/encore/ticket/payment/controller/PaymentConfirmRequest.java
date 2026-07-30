package com.encore.ticket.payment.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

record PaymentConfirmRequest(
        @NotBlank String paymentKey,
        @NotBlank String orderId,
        @NotNull @Positive Long amount) {
}
