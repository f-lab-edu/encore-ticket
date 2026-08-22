package com.encore.ticket.booking.controller;

import com.encore.ticket.core.booking.dto.QueueStatusResponse;
import com.encore.ticket.core.booking.dto.QueueTokenResponse;
import com.encore.ticket.core.booking.queue.application.QueueService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/{scheduleId}/tokens")
    QueueTokenResponse enter(
            @PathVariable("scheduleId") long scheduleId,
            @AuthenticationPrincipal Long memberId) {
        if (!StubSchedules.exists(scheduleId)) {
            throw scheduleNotFound(scheduleId);
        }
        if (!StubSchedules.bookingOpen(scheduleId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "예매 기간이 아닙니다: " + scheduleId);
        }

        return queueService.enter(scheduleId, memberId);
    }

    @GetMapping("/{scheduleId}/status")
    QueueStatusResponse status(
            @PathVariable("scheduleId") long scheduleId,
            @RequestHeader("X-Queue-Token") String queueToken,
            @AuthenticationPrincipal Long memberId) {

        if (!StubSchedules.exists(scheduleId)) {
            throw scheduleNotFound(scheduleId);
        }
        return queueService.status(scheduleId, queueToken, memberId);
    }

    private static ResponseStatusException scheduleNotFound(long scheduleId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 회차입니다: " + scheduleId);
    }
}
