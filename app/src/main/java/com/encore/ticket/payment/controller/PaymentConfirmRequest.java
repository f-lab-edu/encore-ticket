package com.encore.ticket.payment.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

record PaymentConfirmRequest(
        @NotBlank @Size(max = 200) String paymentKey,
        @NotBlank @Size(min = 6, max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String orderId,
        @NotNull @Positive Long amount) {
}
