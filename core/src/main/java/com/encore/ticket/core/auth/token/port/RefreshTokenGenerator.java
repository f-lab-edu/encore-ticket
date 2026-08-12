package com.encore.ticket.core.auth.token.port;

public interface RefreshTokenGenerator {
    public String generate();

    public String hash(String rawToken);
}
