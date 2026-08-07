package com.encore.ticket.core.catalog;

import com.encore.ticket.core.catalog.dto.ConcertStatus;

class Concert {

    private final Long id;
    private final String title;
    private final String description;
    private final String notice;
    private final String posterUrl;
    private final String venue;

    private ConcertStatus status;
    private int likeCount;

    private Concert(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.description = builder.description;
        this.notice = builder.notice;
        this.posterUrl = builder.posterUrl;
        this.venue = builder.venue;
        this.status = builder.status;
        this.likeCount = builder.likeCount;
    }

    static Builder builder() {
        return new Builder();
    }

    void addLike() {
        likeCount++;
    }

    void removeLike() {
        if (likeCount > 0) {
            likeCount--;
        }
    }

    Long id() {
        return id;
    }

    String title() {
        return title;
    }

    String description() {
        return description;
    }

    String notice() {
        return notice;
    }

    String posterUrl() {
        return posterUrl;
    }

    String venue() {
        return venue;
    }

    ConcertStatus status() {
        return status;
    }

    int likeCount() {
        return likeCount;
    }

    static class Builder {

        private Long id;
        private String title;
        private String description;
        private String notice;
        private String posterUrl;
        private String venue;
        private ConcertStatus status;
        private int likeCount;

        Builder id(Long id) {
            this.id = id;
            return this;
        }

        Builder title(String title) {
            this.title = title;
            return this;
        }

        Builder description(String description) {
            this.description = description;
            return this;
        }

        Builder notice(String notice) {
            this.notice = notice;
            return this;
        }

        Builder posterUrl(String posterUrl) {
            this.posterUrl = posterUrl;
            return this;
        }

        Builder venue(String venue) {
            this.venue = venue;
            return this;
        }

        Builder status(ConcertStatus status) {
            this.status = status;
            return this;
        }

        Builder likeCount(int likeCount) {
            this.likeCount = likeCount;
            return this;
        }

        Concert build() {
            return new Concert(this);
        }
    }
}
