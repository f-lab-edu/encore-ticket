package com.encore.ticket.auth.api.dto;

public record TokenRefreshResponse(
        String accessToken,
        String tokenType,
        long expiresIn) {
}
