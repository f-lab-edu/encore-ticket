package com.encore.ticket.core.booking.hold.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.encore.ticket.core.booking.dto.SeatHoldResult;
import com.encore.ticket.core.booking.exception.IdempotencyKeyReusedException;
import com.encore.ticket.core.booking.exception.PurchaseLimitExceededException;
import com.encore.ticket.core.booking.exception.SeatAlreadyHeldException;
import com.encore.ticket.core.booking.hold.port.SeatHoldAcquireResult;
import com.encore.ticket.core.booking.hold.port.SeatHoldAcquisition;
import com.encore.ticket.core.booking.hold.port.SeatHoldRepository;
import com.encore.ticket.core.catalog.domain.SeatInfo;
import com.encore.ticket.core.catalog.port.SeatCatalogReader;
import com.encore.ticket.core.exception.InvalidRequestFieldException;
import com.encore.ticket.core.exception.NotFoundException;

@ExtendWith(MockitoExtension.class)
class SeatHoldServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime EXPIRES_AT =
            OffsetDateTime.parse("2026-08-04T10:07:00Z");
    private static final OffsetDateTime EXPIRES_AT_IN_KST =
            OffsetDateTime.parse("2026-08-04T19:07:00+09:00");
    private static final long SCHEDULE_ID = 1L;
    private static final long OTHER_SCHEDULE_ID = 2L;
    private static final long MEMBER_ID = 100L;
    private static final long SEAT_PRICE = 120_000L;
    private static final String IDEMPOTENCY_KEY = "idem-1";

    @Mock
    SeatHoldRepository seatHoldRepository;

    @Mock
    SeatCatalogReader seatCatalogReader;

    SeatHoldService service;

    @BeforeEach
    void setUp() {
        service = new SeatHoldService(seatHoldRepository, seatCatalogReader, CLOCK);
    }

    @Test
    void 아무도_선점하지_않은_좌석은_선점에_성공한다() {
        givenSeats(seat(1L, SCHEDULE_ID), seat(2L, SCHEDULE_ID));
        givenAcquire(new SeatHoldAcquisition(
                SeatHoldAcquireResult.ACQUIRED, "hold_new", EXPIRES_AT));

        SeatHoldResult result = hold(List.of(1L, 2L));

        assertThat(result.replayed()).isFalse();
        assertThat(result.response().holdId()).isEqualTo("hold_new");
        assertThat(result.response().seatIds()).containsExactly(1L, 2L);
        assertThat(result.response().totalAmount()).isEqualTo(240_000L);
        assertThat(result.response().expiresAt()).isEqualTo(EXPIRES_AT_IN_KST);
    }

    @Test
    void 존재하지_않는_좌석이_섞여_있으면_찾을_수_없다고_알린다() {
        givenSeats(seat(1L, SCHEDULE_ID));

        assertThatThrownBy(() -> hold(List.of(1L, 99L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void 다른_회차의_좌석이_섞여_있으면_잘못된_요청_필드로_알린다() {
        givenSeats(seat(1L, SCHEDULE_ID), seat(2L, OTHER_SCHEDULE_ID));

        assertThatThrownBy(() -> hold(List.of(1L, 2L)))
                .isInstanceOf(InvalidRequestFieldException.class)
                .hasMessageContaining("2");
    }

    @Test
    void 이미_선점된_좌석이_하나라도_있으면_실패한다() {
        givenSeats(seat(1L, SCHEDULE_ID), seat(2L, SCHEDULE_ID));
        givenAcquire(SeatHoldAcquisition.failed(SeatHoldAcquireResult.SEAT_ALREADY_HELD));

        assertThatThrownBy(() -> hold(List.of(1L, 2L)))
                .isInstanceOf(SeatAlreadyHeldException.class);
    }

    @Test
    void 구매_상한을_넘기면_선점에_실패한다() {
        givenSeats(seat(1L, SCHEDULE_ID), seat(2L, SCHEDULE_ID));
        givenAcquire(SeatHoldAcquisition.failed(SeatHoldAcquireResult.PURCHASE_LIMIT_EXCEEDED));

        assertThatThrownBy(() -> hold(List.of(1L, 2L)))
                .isInstanceOf(PurchaseLimitExceededException.class);
    }

    @Test
    void 같은_키로_같은_좌석을_다시_요청하면_최초_선점을_그대로_돌려준다() {
        givenSeats(seat(1L, SCHEDULE_ID), seat(2L, SCHEDULE_ID));
        givenAcquire(new SeatHoldAcquisition(
                SeatHoldAcquireResult.REPLAYED, "hold_first", EXPIRES_AT));

        SeatHoldResult result = hold(List.of(1L, 2L));

        assertThat(result.replayed()).isTrue();
        assertThat(result.response().holdId()).isEqualTo("hold_first");
        assertThat(result.response().expiresAt()).isEqualTo(EXPIRES_AT_IN_KST);
    }

    @Test
    void 같은_키로_다른_좌석을_요청하면_키_재사용으로_거절한다() {
        givenSeats(seat(1L, SCHEDULE_ID), seat(2L, SCHEDULE_ID));
        givenAcquire(SeatHoldAcquisition.failed(SeatHoldAcquireResult.IDEMPOTENCY_KEY_REUSED));

        assertThatThrownBy(() -> hold(List.of(1L, 2L)))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void 좌석_순서가_달라도_같은_요청_지문을_만든다() {
        givenSeats(seat(2L, SCHEDULE_ID), seat(1L, SCHEDULE_ID));
        givenAcquire(new SeatHoldAcquisition(
                SeatHoldAcquireResult.ACQUIRED, "hold_new", EXPIRES_AT));

        hold(List.of(2L, 1L));

        ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
        verify(seatHoldRepository).acquire(
                any(), eq(4), eq(IDEMPOTENCY_KEY), fingerprint.capture());
        assertThat(fingerprint.getValue()).isEqualTo(SCHEDULE_ID + ":1,2");
    }

    private SeatHoldResult hold(List<Long> seatIds) {
        return service.hold(SCHEDULE_ID, seatIds, MEMBER_ID, IDEMPOTENCY_KEY);
    }

    private void givenSeats(SeatInfo... seats) {
        given(seatCatalogReader.seatsByIds(any())).willReturn(List.of(seats));
    }

    private void givenAcquire(SeatHoldAcquisition acquisition) {
        given(seatHoldRepository.acquire(any(), eq(4), any(), any()))
                .willReturn(acquisition);
    }

    private static SeatInfo seat(long seatId, long scheduleId) {
        return new SeatInfo(seatId, scheduleId, "A구역", "1열", seatId + "번", "VIP", SEAT_PRICE);
    }
}
