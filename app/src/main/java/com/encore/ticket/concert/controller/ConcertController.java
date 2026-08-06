package com.encore.ticket.concert.controller;

import com.encore.ticket.catalog.api.dto.ConcertDetailResponse;
import com.encore.ticket.catalog.api.dto.ConcertLikeResponse;
import com.encore.ticket.catalog.api.dto.ConcertRankingResponse;
import com.encore.ticket.catalog.api.dto.ConcertSummaryResponse;
import com.encore.ticket.catalog.api.dto.PageResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/concerts")
public class ConcertController {

    @GetMapping
    PageResponse<ConcertSummaryResponse> concerts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {

        return StubConcertCatalog.page(page, size);
    }

    @GetMapping("/ranking")
    ConcertRankingResponse ranking(@RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return StubConcertCatalog.ranking(limit);
    }

    @GetMapping("/{concertId}")
    ConcertDetailResponse concert(
            @PathVariable("concertId") long concertId,
            @AuthenticationPrincipal Long memberId) {

        return StubConcertCatalog.detail(concertId, memberId)
                .orElseThrow(() -> notFound(concertId));
    }

    @PostMapping("/{concertId}/views")
    ResponseEntity<Void> increaseViewCount(@PathVariable("concertId") long concertId) {
        if (!StubConcertCatalog.exists(concertId)) {
            throw notFound(concertId);
        }

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{concertId}/likes")
    ResponseEntity<ConcertLikeResponse> like(
            @PathVariable("concertId") long concertId,
            @AuthenticationPrincipal Long memberId) {

        if (!StubConcertCatalog.exists(concertId)) {
            throw notFound(concertId);
        }

        StubConcertCatalog.LikeResult result = StubConcertCatalog.like(concertId, memberId);

        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
    }

    @DeleteMapping("/{concertId}/likes")
    ConcertLikeResponse unlike(
            @PathVariable("concertId") long concertId,
            @AuthenticationPrincipal Long memberId) {

        if (!StubConcertCatalog.exists(concertId)) {
            throw notFound(concertId);
        }

        return StubConcertCatalog.unlike(concertId, memberId);
    }

    private static ResponseStatusException notFound(long concertId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 콘서트입니다: " + concertId);
    }
}
