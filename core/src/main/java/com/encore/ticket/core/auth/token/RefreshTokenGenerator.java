package com.encore.ticket.core.auth.token;

interface RefreshTokenGenerator {
    String generate();

    String hash(String rawToken);
}
