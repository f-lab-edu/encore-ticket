package com.encore.ticket.booking.controller;

import com.encore.ticket.core.booking.dto.SeatMapResponse;
import com.encore.ticket.core.booking.exception.QueueNotAdmittedException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/schedules")
public class ScheduleSeatController {

    @GetMapping("/{scheduleId}/seats")
    SeatMapResponse seats(
            @PathVariable("scheduleId") long scheduleId,
            @RequestHeader("X-Queue-Token") String queueToken) {

        if (!StubSchedules.exists(scheduleId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "존재하지 않는 회차입니다: " + scheduleId);
        }
        if (!StubQueue.admitted(queueToken)) {
            throw new QueueNotAdmittedException();
        }

        return StubSeatMap.of(scheduleId);
    }
}
