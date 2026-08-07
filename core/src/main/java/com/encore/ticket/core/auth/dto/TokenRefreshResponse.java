package com.encore.ticket.core.auth.dto;

public record TokenRefreshResponse(
        String accessToken,
        String tokenType,
        long expiresIn) {
}
