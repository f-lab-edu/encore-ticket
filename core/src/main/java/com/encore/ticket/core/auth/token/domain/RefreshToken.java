package com.encore.ticket.core.auth.token.domain;

import java.time.Clock;
import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RefreshToken {

    private static final int IDLE_DAYS = 7;

    private final String tokenHash;
    private final String tokenFamilyId;
    private final Long userId;
    private final OffsetDateTime absoluteExpiresAt;

    private RefreshTokenStatus status;
    private OffsetDateTime idleExpiresAt;

    public static RefreshToken rotatedFrom(RefreshToken previous, String tokenHash, Clock clock) {
        OffsetDateTime idleExpiresAt = OffsetDateTime.now(clock).plusDays(IDLE_DAYS);
        if (previous.absoluteExpiresAt.isBefore(idleExpiresAt)) {
            idleExpiresAt = previous.absoluteExpiresAt;
        }

        return builder()
                .tokenHash(tokenHash)
                .tokenFamilyId(previous.tokenFamilyId)
                .userId(previous.userId)
                .status(RefreshTokenStatus.ACTIVE)
                .idleExpiresAt(idleExpiresAt)
                .absoluteExpiresAt(previous.absoluteExpiresAt)
                .build();
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
}
