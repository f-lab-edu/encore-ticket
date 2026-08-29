package com.encore.ticket.core.payment.application;

import com.encore.ticket.core.payment.domain.Payment;
import com.encore.ticket.core.payment.dto.PaymentStatus;
import com.encore.ticket.core.payment.port.PaymentRepository;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    @Mock PaymentRepository paymentRepository;
    @InjectMocks PaymentQueryService service;

    @Test
    void 선점으로_조회했을_때_결제_시도가_없으면_비어_있다() {
        given(paymentRepository.findLatestByHoldId("hold_7f32")).willReturn(Optional.empty());

        assertThat(service.latestAttemptOf("hold_7f32")).isEmpty();
    }

    @Test
    void 선점으로_조회하면_가장_최근_시도의_상태를_돌려준다() {
        given(paymentRepository.findLatestByHoldId("hold_7f32"))
                .willReturn(Optional.of(Payment.builder()
                        .orderId("reservation-501-2").status(PaymentStatus.FAILED).build()));

        assertThat(service.latestAttemptOf("hold_7f32")).contains(PaymentStatus.FAILED);
    }
}
