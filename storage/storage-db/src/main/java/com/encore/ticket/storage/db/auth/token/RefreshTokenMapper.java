package com.encore.ticket.storage.db.auth.token;

import com.encore.ticket.core.auth.token.domain.RefreshToken;

final class RefreshTokenMapper {

    private RefreshTokenMapper() {
    }

    static RefreshToken toDomain(RefreshTokenEntity entity) {
        return RefreshToken.builder()
                .id(entity.id())
                .tokenHash(entity.tokenHash())
                .tokenFamilyId(entity.tokenFamilyId())
                .memberId(entity.memberId())
                .status(entity.status())
                .idleExpiresAt(entity.idleExpiresAt())
                .absoluteExpiresAt(entity.absoluteExpiresAt())
                .build();
    }

    static RefreshTokenEntity toEntity(RefreshToken refreshToken) {
        return RefreshTokenEntity.builder()
                .id(refreshToken.id())
                .tokenHash(refreshToken.tokenHash())
                .tokenFamilyId(refreshToken.tokenFamilyId())
                .memberId(refreshToken.memberId())
                .status(refreshToken.status())
                .idleExpiresAt(refreshToken.idleExpiresAt())
                .absoluteExpiresAt(refreshToken.absoluteExpiresAt())
                .build();
    }
}
