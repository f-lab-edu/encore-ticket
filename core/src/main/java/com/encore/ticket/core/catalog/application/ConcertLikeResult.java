package com.encore.ticket.core.catalog.application;

import com.encore.ticket.core.catalog.dto.ConcertLikeResponse;

public record ConcertLikeResult(ConcertLikeResponse response, boolean created) {
}
