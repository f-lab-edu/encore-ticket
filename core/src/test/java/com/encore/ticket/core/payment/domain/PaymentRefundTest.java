package com.encore.ticket.core.payment.domain;

import com.encore.ticket.core.payment.dto.PaymentRefundStatus;
import com.encore.ticket.core.payment.dto.PaymentStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRefundTest {

    private static final OffsetDateTime COMPLETED_AT = OffsetDateTime.parse("2026-09-05T01:02:03Z");

    @Test
    void 저장된_승인_결제로부터_멱등한_PENDING_환불을_만든다() {
        Payment payment = payment(42L, "pay-key");

        PaymentRefund first = PaymentRefund.pending(payment, "예매 확정 불가");
        PaymentRefund second = PaymentRefund.pending(payment, "예매 확정 불가");

        assertThat(first).isEqualTo(second);
        assertThat(first.paymentId()).isEqualTo(42L);
        assertThat(first.idempotencyKey()).isEqualTo("refund-pay-key");
        assertThat(first.status()).isEqualTo(PaymentRefundStatus.PENDING);
    }

    @Test
    void PENDING_결제로는_환불을_만들_수_없다() {
        Payment payment = payment(42L, "pay-key").toBuilder()
                .status(PaymentStatus.PENDING)
                .build();

        assertThatThrownBy(() -> PaymentRefund.pending(payment, "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("승인된 결제만 환불할 수 있습니다");
    }

    @Test
    void FAILED_결제로는_환불을_만들_수_없다() {
        Payment payment = payment(42L, "pay-key").toBuilder()
                .status(PaymentStatus.FAILED)
                .build();

        assertThatThrownBy(() -> PaymentRefund.pending(payment, "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("승인된 결제만 환불할 수 있습니다");
    }

    @Test
    void 저장되지_않은_결제로는_환불을_만들_수_없다() {
        assertThatThrownBy(() -> PaymentRefund.pending(payment(null, "pay-key"), "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("저장된 결제만 환불할 수 있습니다");
    }

    @Test
    void PENDING_환불은_완료되며_완료된_재처리는_멱등하다() {
        PaymentRefund pending = PaymentRefund.pending(payment(42L, "pay-key"), "reason");

        PaymentRefund completed = pending.complete(COMPLETED_AT);

        assertThat(completed.status()).isEqualTo(PaymentRefundStatus.COMPLETED);
        assertThat(completed.completedAt()).isEqualTo(COMPLETED_AT);
        assertThat(completed.failureReason()).isNull();
        assertThat(completed.complete(COMPLETED_AT.plusMinutes(1))).isEqualTo(completed);
    }

    @Test
    void PENDING_환불은_실패로_전환되고_실패_재처리는_상태를_보존한다() {
        PaymentRefund pending = PaymentRefund.pending(payment(42L, "pay-key"), "reason");

        PaymentRefund failed = pending.fail("환불 거절");

        assertThat(failed.status()).isEqualTo(PaymentRefundStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo("환불 거절");
        assertThat(failed.fail("another reason")).isEqualTo(failed);
        assertThatThrownBy(() -> failed.complete(COMPLETED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 완료된_환불은_다시_실패로_되돌리지_않는다() {
        PaymentRefund completed = PaymentRefund.pending(payment(42L, "pay-key"), "reason")
                .complete(COMPLETED_AT);

        assertThat(completed.fail("late failure")).isEqualTo(completed);
    }

    private Payment payment(Long id, String paymentKey) {
        return Payment.builder()
                .id(id)
                .paymentKey(paymentKey)
                .orderId("order-42")
                .amount(1000L)
                .reservationId(7L)
                .memberId(9L)
                .holdId("hold-42")
                .status(PaymentStatus.COMPLETED)
                .build();
    }
}
