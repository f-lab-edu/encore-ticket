package com.encore.ticket.config;

import com.encore.ticket.core.payment.application.PaymentService;
import com.encore.ticket.payment.PaymentRecoveryScheduler;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class PaymentSchedulingIsolationTest {

    @Test
    void 결제_조회가_대기해도_일반_스케줄러는_계속_실행된다() throws Exception {
        CountDownLatch paymentStarted = new CountDownLatch(1);
        CountDownLatch releasePayment = new CountDownLatch(1);
        PaymentService service = mock(PaymentService.class);
        doAnswer(invocation -> {
            paymentStarted.countDown();
            releasePayment.await(5, TimeUnit.SECONDS);
            return 0;
        }).when(service).recoverPending(any(), anyInt());
        DefaultProbe probe = new DefaultProbe(paymentStarted);

        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(SchedulingConfig.class);
            context.registerBean(DefaultProbe.class, () -> probe);
            context.registerBean(PaymentRecoveryScheduler.class, () ->
                    new PaymentRecoveryScheduler(service, Clock.systemUTC(), Duration.ZERO, 20));
            context.refresh();
            try {
                assertThat(paymentStarted.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(probe.ranWhilePaymentBlocked.await(2, TimeUnit.SECONDS)).isTrue();
            } finally {
                releasePayment.countDown();
            }
        }
    }

    static class DefaultProbe {
        private final CountDownLatch paymentStarted;
        private final CountDownLatch ranWhilePaymentBlocked = new CountDownLatch(1);

        DefaultProbe(CountDownLatch paymentStarted) {
            this.paymentStarted = paymentStarted;
        }

        @Scheduled(fixedDelay = 10)
        public void run() {
            if (paymentStarted.getCount() == 0) {
                ranWhilePaymentBlocked.countDown();
            }
        }
    }
}
