package com.encore.ticket.core.auth.token.port;

import java.util.Optional;
import com.encore.ticket.core.auth.token.domain.RefreshToken;

public interface RefreshTokenRepository {
    public Optional<RefreshToken> findByTokenHash(String tokenHash);

    public void save(RefreshToken refreshToken);

    public void revokeFamily(String tokenFamilyId);
}
