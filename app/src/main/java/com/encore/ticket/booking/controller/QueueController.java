package com.encore.ticket.booking.controller;

import com.encore.ticket.booking.api.dto.QueueStatusResponse;
import com.encore.ticket.booking.api.dto.QueueTokenResponse;
import com.encore.ticket.booking.api.exception.QueueTokenExpiredException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/queue")
public class QueueController {

    @PostMapping("/{scheduleId}/tokens")
    QueueTokenResponse enter(@PathVariable("scheduleId") long scheduleId) {
        if (!StubSchedules.exists(scheduleId)) {
            throw scheduleNotFound(scheduleId);
        }
        if (!StubSchedules.bookingOpen(scheduleId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "예매 기간이 아닙니다: " + scheduleId);
        }

        return StubQueue.enter(scheduleId);
    }

    @GetMapping("/{scheduleId}/status")
    QueueStatusResponse status(
            @PathVariable("scheduleId") long scheduleId,
            @RequestHeader("X-Queue-Token") String queueToken) {

        if (!StubSchedules.exists(scheduleId)) {
            throw scheduleNotFound(scheduleId);
        }
        if (!StubQueue.exists(queueToken)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "존재하지 않는 대기열 토큰입니다.");
        }
        if (StubQueue.expired(queueToken)) {
            throw new QueueTokenExpiredException();
        }

        return StubQueue.status(queueToken);
    }

    private static ResponseStatusException scheduleNotFound(long scheduleId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 회차입니다: " + scheduleId);
    }
}
