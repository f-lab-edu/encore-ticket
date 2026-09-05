package com.encore.ticket.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.encore.ticket.ApiSpecTestSupport;
import com.encore.ticket.core.auth.token.application.TokenService;
import com.encore.ticket.core.booking.hold.application.SeatHoldService;
import com.encore.ticket.core.booking.hold.application.SeatMapService;
import com.encore.ticket.core.booking.queue.application.QueueService;
import com.encore.ticket.core.booking.reservation.application.ReservationCreateService;
import com.encore.ticket.core.booking.reservation.application.ReservationQueryService;
import com.encore.ticket.core.booking.reservation.application.ReservationService;
import com.encore.ticket.core.catalog.application.ConcertLikeService;
import com.encore.ticket.core.catalog.application.ConcertQueryService;
import com.encore.ticket.core.catalog.application.ConcertRankingService;
import com.encore.ticket.core.payment.application.PaymentService;
import com.encore.ticket.core.payment.application.PaymentQueryService;

class CoreServiceBeanTest extends ApiSpecTestSupport {

    @Autowired
    ApplicationContext context;

    @Test
    void 구현이_있는_서비스가_빈으로_올라온다() {
        assertThat(context.getBeanNamesForType(QueueService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(SeatHoldService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(SeatMapService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(ReservationCreateService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(ReservationService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(ReservationQueryService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(ConcertQueryService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(ConcertLikeService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(PaymentQueryService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(PaymentService.class)).hasSize(1);
    }

    @Test
    void 구현이_없는_포트에_의존하는_셋은_빈이_아니다() {
        assertThat(context.getBeanNamesForType(TokenService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ConcertRankingService.class)).isEmpty();
    }
}
