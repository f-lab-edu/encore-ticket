package com.encore.ticket.core.catalog.dto;

public record ConcertLikeResponse(
        long concertId,
        boolean liked,
        int likeCount) {
}
