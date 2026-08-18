package com.encore.ticket.core.auth.token.domain;

import java.time.Clock;
import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class RefreshToken {

    private static final int IDLE_DAYS = 7;

    private final Long id;
    private final String tokenHash;
    private final String tokenFamilyId;
    private final Long memberId;
    private final OffsetDateTime absoluteExpiresAt;

    private final RefreshTokenStatus status;
    private final OffsetDateTime idleExpiresAt;

    public static RefreshToken rotatedFrom(RefreshToken previous, String tokenHash, Clock clock) {
        OffsetDateTime idleExpiresAt = OffsetDateTime.now(clock).plusDays(IDLE_DAYS);
        if (previous.absoluteExpiresAt.isBefore(idleExpiresAt)) {
            idleExpiresAt = previous.absoluteExpiresAt;
        }

        return builder()
                .tokenHash(tokenHash)
                .tokenFamilyId(previous.tokenFamilyId)
                .memberId(previous.memberId)
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

    public RefreshToken rotate() {
        return toBuilder()
                .status(RefreshTokenStatus.ROTATED)
                .build();
    }
}
