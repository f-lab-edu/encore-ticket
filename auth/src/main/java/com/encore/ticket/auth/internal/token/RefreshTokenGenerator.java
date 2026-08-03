package com.encore.ticket.auth.internal.token;

interface RefreshTokenGenerator {
    String generate();

    String hash(String rawToken);
}
