package com.encore.ticket.storage.redis.booking.queue;

import java.time.Duration;

import com.encore.ticket.core.booking.queue.domain.QueueAdmissionPolicy;
import com.encore.ticket.core.booking.queue.domain.QueueAuthorizationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class QueueRedisConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(QueueRedisConfig.class);

    @Test
    void 설정이_없으면_기존_기본값으로_정책을_생성한다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(QueueAdmissionPolicy.class)).isEqualTo(
                    new QueueAdmissionPolicy(100, 500, 100, 1000,
                            Duration.ofMinutes(5), Duration.ofMinutes(5),
                            Duration.ofMinutes(30), Duration.ofMillis(900)));
            assertThat(context.getBean(QueueAuthorizationPolicy.class).renewalWindow())
                    .isEqualTo(Duration.ofMinutes(5));
        });
    }

    @Test
    void 기존_설정_이름으로_지정한_값을_정책에_반영한다() {
        contextRunner.withPropertyValues(
                "ticket.queue.admission.per-schedule-capacity=20",
                "ticket.queue.admission.global-capacity=80",
                "ticket.queue.admission.max-per-run=7",
                "ticket.queue.admission.candidate-scan-limit=60",
                "ticket.queue.admission.waiting-activity-window=2m",
                "ticket.queue.admission.initial-lease=45s",
                "ticket.queue.admission.hard-cap=10m",
                "ticket.queue.admission.execution-lease=250ms",
                "ticket.queue.authorization.renewal-window=90s"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(QueueAdmissionPolicy.class)).isEqualTo(
                    new QueueAdmissionPolicy(20, 80, 7, 60,
                            Duration.ofMinutes(2), Duration.ofSeconds(45),
                            Duration.ofMinutes(10), Duration.ofMillis(250)));
            assertThat(context.getBean(QueueAuthorizationPolicy.class).renewalWindow())
                    .isEqualTo(Duration.ofSeconds(90));
        });
    }

    @Test
    void 일부_설정만_지정하면_나머지는_기본값을_유지한다() {
        contextRunner.withPropertyValues("ticket.queue.admission.max-per-run=12")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(QueueAdmissionPolicy.class)).isEqualTo(
                            new QueueAdmissionPolicy(100, 500, 12, 1000,
                                    Duration.ofMinutes(5), Duration.ofMinutes(5),
                                    Duration.ofMinutes(30), Duration.ofMillis(900)));
                });
    }

    @Test
    void 회차별_정원이_전체_정원보다_크면_시작을_거절한다() {
        contextRunner.withPropertyValues("ticket.queue.admission.per-schedule-capacity=501")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test
    void 갱신_시간이_0이면_시작을_거절한다() {
        contextRunner.withPropertyValues("ticket.queue.authorization.renewal-window=0s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }
}
