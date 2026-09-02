package com.encore.ticket.booking.controller;

import com.encore.ticket.core.booking.dto.SeatMapResponse;
import com.encore.ticket.core.booking.hold.application.SeatMapService;
import com.encore.ticket.core.booking.queue.application.QueueAuthorizationService;
import com.encore.ticket.core.catalog.port.ScheduleCatalogReader;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/schedules")
public class ScheduleSeatController {

    private final QueueAuthorizationService queueAuthorizationService;
    private final SeatMapService seatMapService;
    private final ScheduleCatalogReader scheduleCatalogReader;

    public ScheduleSeatController(
            QueueAuthorizationService queueAuthorizationService,
            SeatMapService seatMapService,
            ScheduleCatalogReader scheduleCatalogReader) {
        this.queueAuthorizationService = queueAuthorizationService;
        this.seatMapService = seatMapService;
        this.scheduleCatalogReader = scheduleCatalogReader;
    }

    @GetMapping("/{scheduleId}/seats")
    SeatMapResponse seats(
            @PathVariable("scheduleId") long scheduleId,
            @RequestHeader("X-Queue-Token") String queueToken,
            @AuthenticationPrincipal Long memberId) {

        scheduleCatalogReader.scheduleOf(scheduleId);
        queueAuthorizationService.authorize(scheduleId, memberId, queueToken);

        return seatMapService.seatMap(scheduleId);
    }
}
