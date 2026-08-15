package com.encore.ticket.core.catalog.domain;

import com.encore.ticket.core.catalog.dto.ConcertStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Concert {

    private final Long id;
    private final String title;
    private final String description;
    private final String notice;
    private final String posterUrl;
    private final String venue;

    private ConcertStatus status;
    private int likeCount;

    public void addLike() {
        likeCount++;
    }

    public void removeLike() {
        if (likeCount > 0) {
            likeCount--;
        }
    }
}
