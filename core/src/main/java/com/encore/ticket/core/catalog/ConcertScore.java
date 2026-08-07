package com.encore.ticket.core.catalog;

record ConcertScore(Long concertId, String title, String posterUrl, int viewCount, int likeCount) {

    private static final int VIEW_POINT = 1;
    private static final int LIKE_POINT = 3;

    int score() {
        return viewCount * VIEW_POINT + likeCount * LIKE_POINT;
    }
}
