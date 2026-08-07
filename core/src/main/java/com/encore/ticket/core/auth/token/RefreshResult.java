package com.encore.ticket.core.auth.token;

import com.encore.ticket.core.auth.dto.TokenRefreshResponse;

record RefreshResult(TokenRefreshResponse response, String refreshToken) {
}
