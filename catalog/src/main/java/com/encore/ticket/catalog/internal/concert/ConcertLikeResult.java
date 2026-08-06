package com.encore.ticket.catalog.internal.concert;

import com.encore.ticket.catalog.api.dto.ConcertLikeResponse;

record ConcertLikeResult(ConcertLikeResponse response, boolean created) {
}
