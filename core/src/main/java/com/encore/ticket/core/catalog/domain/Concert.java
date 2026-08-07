package com.encore.ticket.core.catalog.domain;

import com.encore.ticket.core.catalog.dto.ConcertStatus;

public class Concert {

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

    public static Builder builder() {
        return new Builder();
    }

    public void addLike() {
        likeCount++;
    }

    public void removeLike() {
        if (likeCount > 0) {
            likeCount--;
        }
    }

    public Long id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String notice() {
        return notice;
    }

    public String posterUrl() {
        return posterUrl;
    }

    public String venue() {
        return venue;
    }

    public ConcertStatus status() {
        return status;
    }

    public int likeCount() {
        return likeCount;
    }

    public static class Builder {

        private Long id;
        private String title;
        private String description;
        private String notice;
        private String posterUrl;
        private String venue;
        private ConcertStatus status;
        private int likeCount;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder notice(String notice) {
            this.notice = notice;
            return this;
        }

        public Builder posterUrl(String posterUrl) {
            this.posterUrl = posterUrl;
            return this;
        }

        public Builder venue(String venue) {
            this.venue = venue;
            return this;
        }

        public Builder status(ConcertStatus status) {
            this.status = status;
            return this;
        }

        public Builder likeCount(int likeCount) {
            this.likeCount = likeCount;
            return this;
        }

        public Concert build() {
            return new Concert(this);
        }
    }
}
