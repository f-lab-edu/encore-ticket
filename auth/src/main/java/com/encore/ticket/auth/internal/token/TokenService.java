package com.encore.ticket.auth.internal.token;

import com.encore.ticket.auth.api.dto.TokenRefreshResponse;
import com.encore.ticket.auth.api.exception.InvalidRefreshTokenException;

import java.time.Clock;
import java.util.Optional;

class TokenService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final long ACCESS_TOKEN_SECONDS = 900;

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final AccessTokenIssuer accessTokenIssuer;
    private final Clock clock;

    TokenService(RefreshTokenRepository refreshTokenRepository, RefreshTokenGenerator refreshTokenGenerator,
                 AccessTokenIssuer accessTokenIssuer, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.accessTokenIssuer = accessTokenIssuer;
        this.clock = clock;
    }

    RefreshResult refresh(String rawToken) {
        RefreshToken token = find(rawToken).orElseThrow(InvalidRefreshTokenException::new);

        if (token.isRotated()) {
            refreshTokenRepository.revokeFamily(token.tokenFamilyId());
            throw new InvalidRefreshTokenException();
        }
        if (!token.isActive() || token.isExpired(clock)) {
            throw new InvalidRefreshTokenException();
        }

        token.rotate();
        refreshTokenRepository.save(token);

        String newRawToken = refreshTokenGenerator.generate();
        refreshTokenRepository.save(
                RefreshToken.rotatedFrom(token, refreshTokenGenerator.hash(newRawToken), clock));

        TokenRefreshResponse response = new TokenRefreshResponse(
                accessTokenIssuer.issue(token.userId()), TOKEN_TYPE, ACCESS_TOKEN_SECONDS);

        return new RefreshResult(response, newRawToken);
    }

    void logout(String rawToken) {
        find(rawToken).ifPresent(token -> refreshTokenRepository.revokeFamily(token.tokenFamilyId()));
    }

    private Optional<RefreshToken> find(String rawToken) {
        if (rawToken == null) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHash(refreshTokenGenerator.hash(rawToken));
    }
}
