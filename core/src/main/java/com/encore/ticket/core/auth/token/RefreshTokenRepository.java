package com.encore.ticket.core.auth.token;

import java.util.Optional;

interface RefreshTokenRepository {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void save(RefreshToken refreshToken);

    void revokeFamily(String tokenFamilyId);
}
