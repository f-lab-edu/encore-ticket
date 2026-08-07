package com.encore.ticket.core.auth.token.domain;

import java.time.Clock;
import java.time.OffsetDateTime;

public class RefreshToken {

    private static final int IDLE_DAYS = 7;

    private final String tokenHash;
    private final String tokenFamilyId;
    private final Long userId;
    private final OffsetDateTime absoluteExpiresAt;

    private RefreshTokenStatus status;
    private OffsetDateTime idleExpiresAt;

    public RefreshToken(String tokenHash, String tokenFamilyId, Long userId, RefreshTokenStatus status,
                 OffsetDateTime idleExpiresAt, OffsetDateTime absoluteExpiresAt) {
        this.tokenHash = tokenHash;
        this.tokenFamilyId = tokenFamilyId;
        this.userId = userId;
        this.status = status;
        this.idleExpiresAt = idleExpiresAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
    }

    public static RefreshToken rotatedFrom(RefreshToken previous, String tokenHash, Clock clock) {
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

    public boolean isActive() {
        return status == RefreshTokenStatus.ACTIVE;
    }

    public boolean isRotated() {
        return status == RefreshTokenStatus.ROTATED;
    }

    public boolean isExpired(Clock clock) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return !now.isBefore(idleExpiresAt) || !now.isBefore(absoluteExpiresAt);
    }

    public void rotate() {
        status = RefreshTokenStatus.ROTATED;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public String tokenFamilyId() {
        return tokenFamilyId;
    }

    public Long userId() {
        return userId;
    }

    public RefreshTokenStatus status() {
        return status;
    }

    public OffsetDateTime idleExpiresAt() {
        return idleExpiresAt;
    }

    public OffsetDateTime absoluteExpiresAt() {
        return absoluteExpiresAt;
    }
}
