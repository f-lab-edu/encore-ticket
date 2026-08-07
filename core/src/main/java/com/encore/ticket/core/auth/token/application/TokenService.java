package com.encore.ticket.core.auth.token.application;

import com.encore.ticket.core.auth.dto.TokenRefreshResponse;
import com.encore.ticket.core.auth.exception.InvalidRefreshTokenException;

import java.time.Clock;
import java.util.Optional;
import com.encore.ticket.core.auth.token.domain.RefreshToken;
import com.encore.ticket.core.auth.token.port.AccessTokenIssuer;
import com.encore.ticket.core.auth.token.port.RefreshTokenGenerator;
import com.encore.ticket.core.auth.token.port.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TokenService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final long ACCESS_TOKEN_SECONDS = 900;

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final AccessTokenIssuer accessTokenIssuer;
    private final Clock clock;

    public RefreshResult refresh(String rawToken) {
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

    public void logout(String rawToken) {
        find(rawToken).ifPresent(token -> refreshTokenRepository.revokeFamily(token.tokenFamilyId()));
    }

    private Optional<RefreshToken> find(String rawToken) {
        if (rawToken == null) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHash(refreshTokenGenerator.hash(rawToken));
    }
}
