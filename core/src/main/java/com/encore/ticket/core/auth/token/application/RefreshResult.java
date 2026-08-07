package com.encore.ticket.core.auth.token.application;

import com.encore.ticket.core.auth.dto.TokenRefreshResponse;

public record RefreshResult(TokenRefreshResponse response, String refreshToken) {
}
