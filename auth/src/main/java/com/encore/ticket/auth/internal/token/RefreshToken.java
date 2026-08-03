package com.encore.ticket.auth.internal.token;

import java.time.Clock;
import java.time.OffsetDateTime;

class RefreshToken {

    private static final int IDLE_DAYS = 7;

    private final String tokenHash;
    private final String tokenFamilyId;
    private final Long userId;
    private final OffsetDateTime absoluteExpiresAt;

    private RefreshTokenStatus status;
    private OffsetDateTime idleExpiresAt;

    RefreshToken(String tokenHash, String tokenFamilyId, Long userId, RefreshTokenStatus status,
                 OffsetDateTime idleExpiresAt, OffsetDateTime absoluteExpiresAt) {
        this.tokenHash = tokenHash;
        this.tokenFamilyId = tokenFamilyId;
        this.userId = userId;
        this.status = status;
        this.idleExpiresAt = idleExpiresAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
    }

    static RefreshToken rotatedFrom(RefreshToken previous, String tokenHash, Clock clock) {
        OffsetDateTime idleExpiresAt = OffsetDateTime.now(clock).plusDays(IDLE_DAYS);
        if (previous.absoluteExpiresAt.isBefore(idleExpiresAt)) {
            idleExpiresAt = previous.absoluteExpiresAt;
        }

        return new RefreshToken(
                tokenHash,
                previous.tokenFamilyId,
                previous.userId,
                RefreshTokenStatus.ACTIVE,
                idleExpiresAt,
                previous.absoluteExpiresAt);
    }

    boolean isActive() {
        return status == RefreshTokenStatus.ACTIVE;
    }

    boolean isRotated() {
        return status == RefreshTokenStatus.ROTATED;
    }

    boolean isExpired(Clock clock) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return !now.isBefore(idleExpiresAt) || !now.isBefore(absoluteExpiresAt);
    }

    void rotate() {
        status = RefreshTokenStatus.ROTATED;
    }

    String tokenHash() {
        return tokenHash;
    }

    String tokenFamilyId() {
        return tokenFamilyId;
    }

    Long userId() {
        return userId;
    }

    RefreshTokenStatus status() {
        return status;
    }

    OffsetDateTime idleExpiresAt() {
        return idleExpiresAt;
    }

    OffsetDateTime absoluteExpiresAt() {
        return absoluteExpiresAt;
    }
}
