package com.encore.ticket.catalog.internal.concert;

class Concert {

    private final Long id;

    private int likeCount;

    Concert(Long id, int likeCount) {
        this.id = id;
        this.likeCount = likeCount;
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

    int likeCount() {
        return likeCount;
    }
}
