package com.encore.ticket.catalog.api.dto;

public record ConcertLikeResponse(
        long concertId,
        boolean liked,
        int likeCount) {
}
