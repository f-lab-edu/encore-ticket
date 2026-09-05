package com.encore.ticket.payment;

import com.encore.ticket.core.payment.application.PaymentService;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentRecoveryScheduler {

    private final PaymentService paymentService;
    private final Clock clock;
    private final Duration recoveryDelay;
    private final int batchSize;

    public PaymentRecoveryScheduler(
            PaymentService paymentService,
            Clock clock,
            @Value("${ticket.payment.recovery.delay:70s}") Duration recoveryDelay,
            @Value("${ticket.payment.recovery.batch-size:20}") int batchSize) {
        if (recoveryDelay.isNegative()) {
            throw new IllegalArgumentException("결제 복구 지연 시간은 0 이상이어야 합니다");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("결제 복구 batch 크기는 1 이상이어야 합니다: " + batchSize);
        }
        this.paymentService = paymentService;
        this.clock = clock;
        this.recoveryDelay = recoveryDelay;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${ticket.payment.recovery.scheduler-interval:10s}",
            scheduler = "paymentRecoveryTaskScheduler")
    public void recover() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minus(recoveryDelay);
        try {
            int paymentCount = paymentService.recoverPending(cutoff, batchSize);
            int refundCount = paymentService.recoverRefunds(cutoff, batchSize);
            if (paymentCount > 0 || refundCount > 0) {
                log.info(
                        "event=payment_recovery_scanned paymentCount={} refundCount={} cutoff={}",
                        paymentCount, refundCount, cutoff);
            }
        } catch (RuntimeException exception) {
            log.error(
                    "event=payment_recovery_failed batchSize={} cutoff={} errorType={}",
                    batchSize, cutoff, exception.getClass().getSimpleName(), exception);
        }
    }
}
