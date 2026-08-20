package com.encore.ticket.core.booking.hold.application;


import com.encore.ticket.core.booking.dto.SeatMapResponse;
import com.encore.ticket.core.booking.dto.SeatStatus;
import com.encore.ticket.core.catalog.port.SeatCatalogReader;
import com.encore.ticket.core.catalog.domain.SeatInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.groups.Tuple.*;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;
import com.encore.ticket.core.booking.hold.port.SeatHoldRepository;
import com.encore.ticket.core.booking.seat.port.SeatAssignmentReader;


@ExtendWith(MockitoExtension.class)
class SeatMapServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC);
    private static final long SCHEDULE_ID = 1L;

    @Mock
    SeatHoldRepository seatHoldRepository;

    @Mock
    SeatAssignmentReader seatAssignmentReader;

    @Mock
    SeatCatalogReader seatCatalogReader;

    SeatMapService service;

    @BeforeEach
    void setup() {
        service = new SeatMapService(seatHoldRepository, seatAssignmentReader, seatCatalogReader, CLOCK);
    }

    @Test
    void catalog의_좌석_정보에_booking의_상태를_얹어_돌려준다() {
        given(seatCatalogReader.seatsOf(SCHEDULE_ID)).willReturn(List.of(
                new SeatInfo(1001L, "A구역", "1열", "1번", "VIP", 165_000L)));

        given(seatHoldRepository.holdExpiryBySeatId(SCHEDULE_ID))
                .willReturn(Map.of(1001L, OffsetDateTime.parse("2026-08-04T10:05:00Z")));

        SeatMapResponse response = service.seatMap(SCHEDULE_ID);

        assertThat(response.scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(response.seats()).hasSize(1);

        SeatMapResponse.Seat seat = response.seats().get(0);
        assertThat(seat.id()).isEqualTo(1001L);
        assertThat(seat.section()).isEqualTo("A구역");
        assertThat(seat.row()).isEqualTo("1열");
        assertThat(seat.number()).isEqualTo("1번");
        assertThat(seat.grade()).isEqualTo("VIP");
        assertThat(seat.price()).isEqualTo(165_000L);
        assertThat(seat.status()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void 예매된_좌석은_RESERVED_선점된_좌석은_HELD_다() {
        given(seatCatalogReader.seatsOf(SCHEDULE_ID)).willReturn(List.of(
                new SeatInfo(1001L, "A구역", "1열", "1번", "VIP", 165_000L),
                new SeatInfo(1002L, "A구역", "1열", "2번", "VIP", 165_000L),
                new SeatInfo(1003L, "A구역", "1열", "3번", "VIP", 165_000L)));

        given(seatHoldRepository.holdExpiryBySeatId(SCHEDULE_ID)).willReturn(Map.of(
                1002L, OffsetDateTime.parse("2026-08-04T10:05:00Z"),
                1003L, OffsetDateTime.parse("2026-08-04T10:05:00Z")));

        given(seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID)).willReturn(Set.of(1003L));

        SeatMapResponse response = service.seatMap(SCHEDULE_ID);

        assertThat(response.seats())
                .extracting(SeatMapResponse.Seat::id, SeatMapResponse.Seat::status)
                .containsExactly(
                        tuple(1001L, SeatStatus.AVAILABLE),
                        tuple(1002L, SeatStatus.HELD),
                        tuple(1003L, SeatStatus.RESERVED)
                );
    }

    @Test
    void 만료된_선점은_AVAILABLE_로_보인다() {
        given(seatCatalogReader.seatsOf(SCHEDULE_ID)).willReturn(List.of(
                new SeatInfo(1001L, "A구역", "1열", "1번", "VIP", 165_000L)));

        given(seatHoldRepository.holdExpiryBySeatId(SCHEDULE_ID))
                .willReturn(Map.of(1001L, OffsetDateTime.parse("2026-08-04T10:00:00Z")));

        SeatMapResponse response = service.seatMap(SCHEDULE_ID);

        assertThat(response.seats().get(0).status()).isEqualTo(SeatStatus.AVAILABLE);
    }

}
