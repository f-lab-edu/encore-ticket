package com.encore.ticket.auth.internal.token;

import com.encore.ticket.auth.api.dto.TokenRefreshResponse;

record RefreshResult(TokenRefreshResponse response, String refreshToken) {
}
