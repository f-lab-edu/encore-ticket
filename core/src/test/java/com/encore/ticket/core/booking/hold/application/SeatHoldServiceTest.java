package com.encore.ticket.core.booking.hold.application;

import com.encore.ticket.core.booking.dto.SeatHoldResponse;
import com.encore.ticket.core.booking.exception.PurchaseLimitExceededException;
import com.encore.ticket.core.booking.exception.SeatAlreadyHeldException;
import com.encore.ticket.core.catalog.port.SeatCatalogReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.encore.ticket.core.booking.hold.port.SeatHoldRepository;

@ExtendWith(MockitoExtension.class)
class SeatHoldServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC);
    private static final long SCHEDULE_ID = 1L;
    private static final long MEMBER_ID = 100L;

    @Mock SeatHoldRepository seatHoldRepository;
    @Mock
    SeatCatalogReader seatCatalogReader;

    SeatHoldService service;

    @BeforeEach
    void setUp() {
        service = new SeatHoldService(seatHoldRepository, seatCatalogReader, CLOCK);
    }

    @Test
    void 아무도_선점하지_않은_좌석은_선점에_성공한다() {
        given(seatCatalogReader.pricesOf(List.of(1L, 2L)))
                .willReturn(Map.of(1L, 120_000L, 2L, 120_000L));

        SeatHoldResponse response = service.hold(SCHEDULE_ID, List.of(1L, 2L), MEMBER_ID);

        assertThat(response.seatIds()).containsExactly(1L, 2L);
        assertThat(response.totalAmount()).isEqualTo(240_000L);
        assertThat(response.expiresAt()).isEqualTo(OffsetDateTime.parse("2026-08-04T10:07:00Z"));
        verify(seatHoldRepository).save(any());
    }

    @Test
    void 이미_선점된_좌석이_하나라도_있으면_실패한다() {
        given(seatHoldRepository.findOccupiedSeatIds(SCHEDULE_ID)).willReturn(Set.of(2L));

        assertThatThrownBy(() -> service.hold(SCHEDULE_ID, List.of(1L, 2L), MEMBER_ID))
                .isInstanceOf(SeatAlreadyHeldException.class);

        verify(seatHoldRepository, never()).save(any());
    }

    @Test
    void 구매_상한을_넘기면_선점에_실패한다() {
        given(seatHoldRepository.countActiveSeatsOf(SCHEDULE_ID, MEMBER_ID)).willReturn(3);

        assertThatThrownBy(() -> service.hold(SCHEDULE_ID, List.of(1L, 2L), MEMBER_ID))
                .isInstanceOf(PurchaseLimitExceededException.class);

        verify(seatHoldRepository, never()).save(any());
    }

    // 현재 좌석이 회차에 속하는지는 app이 검증한다. - 도메인 예외로 옮기면
    // errors[].field 를 못 채워 계약이 깨진다. 번역 경로가 생기면 이 클래스로 옮긴다.
}
