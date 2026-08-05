package com.encore.ticket.auth.api.dto;

import com.encore.ticket.auth.api.AuthProvider;

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
