package com.encore.ticket.core.auth.dto;

import com.encore.ticket.core.auth.AuthProvider;

public record SocialLoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        User user) {

    public record User(
            long id,
            String name,
            AuthProvider provider,
            boolean isNewUser) {
    }
}
